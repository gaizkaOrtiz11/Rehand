package com.rehand.web;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.beans.PropertyAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.TransactionType;
import com.rehand.domain.WalletTransaction;
import com.rehand.dto.PageNav;
import com.rehand.dto.PaymentChecks;
import com.rehand.dto.ReturnTo;
import com.rehand.dto.TopUpForm;
import com.rehand.dto.WalletHistoryFilter;
import com.rehand.dto.WithdrawForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.WalletService;

/**
 * Wallet pages: balance and history (GET /wallet), simulated top-up and withdrawal.
 * Every POST follows post/redirect/get and reports the result as a flash message.
 * Card and bank details are validated and discarded: never stored, logged or echoed back.
 * All routes need a login (SecurityConfig: anyRequest().authenticated()).
 */
@Controller
@RequestMapping("/wallet")
public class WalletController {

    static final int PAGE_SIZE = 12;

    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** A number field with letters in it gets a readable message instead of Spring's conversion text. */
    @InitBinder({"topUpForm", "withdrawForm"})
    void friendlyNumberErrors(WebDataBinder binder) {
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult result) {
                String field = ex.getPropertyName();
                result.addError(new FieldError(result.getObjectName(), field, null, true,
                        new String[] {"typeMismatch"}, null, "Enter an amount in euros, for example 25.50."));
            }
        });
    }

    // ================================================================ overview

    @GetMapping
    public String overview(@AuthenticationPrincipal AppUserDetails me,
                           @ModelAttribute("filter") WalletHistoryFilter filter,
                           BindingResult ignoredBindingErrors,
                           Model model) {
        Long userId = me.getId();
        Page<WalletTransaction> page = walletService.history(userId, filter, PageRequest.of(filter.getPageIndex(), PAGE_SIZE));
        if (page.getTotalPages() > 0 && filter.getPageIndex() >= page.getTotalPages()) {
            filter.setPage(page.getTotalPages());
            page = walletService.history(userId, filter, PageRequest.of(filter.getPageIndex(), PAGE_SIZE));
        }
        YearMonth month = YearMonth.now();

        model.addAttribute("balance", walletService.balanceOf(userId));
        model.addAttribute("summary", walletService.monthSummary(userId, month));
        model.addAttribute("monthLabel", month.format(MONTH_LABEL));
        model.addAttribute("monthStart", month.atDay(1));
        model.addAttribute("page", page);
        model.addAttribute("pageWindow", PageNav.window(page.getNumber() + 1, page.getTotalPages()));
        model.addAttribute("pageBase", "/wallet?" + filter.pageLinkPrefix());
        model.addAttribute("types", TransactionType.values());
        return "wallet/index";
    }

    // ================================================================ top-up

    @GetMapping("/top-up")
    public String topUpForm(@AuthenticationPrincipal AppUserDetails me,
                            @RequestParam(name = "amount", required = false) String amount,
                            @RequestParam(name = "returnTo", required = false) String returnTo,
                            Model model) {
        model.addAttribute("topUpForm", TopUpForm.prefilled(parseAmount(amount), returnTo));
        return topUpPage(me, model);
    }

    @PostMapping("/top-up")
    public String topUp(@AuthenticationPrincipal AppUserDetails me,
                        @Valid @ModelAttribute("topUpForm") TopUpForm form,
                        BindingResult result,
                        Model model,
                        RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                BigDecimal amount = form.getAmount();
                walletService.topUp(me.getId(), amount);
                redirect.addFlashAttribute(FLASH_SUCCESS, euros(amount) + " has been added to your wallet. New balance: "
                        + euros(walletService.balanceOf(me.getId())) + ".");
                String target = ReturnTo.sanitize(form.getReturnTo());
                return "redirect:" + (target != null ? target : "/wallet");
            } catch (BusinessRuleException e) {
                result.reject("topUp.failed", e.getMessage());
            }
        }
        form.clearSensitive();
        return topUpPage(me, model);
    }

    private String topUpPage(AppUserDetails me, Model model) {
        model.addAttribute("balance", walletService.balanceOf(me.getId()));
        model.addAttribute("presets", TopUpForm.PRESETS);
        return "wallet/top-up";
    }

    // ================================================================ withdraw

    @GetMapping("/withdraw")
    public String withdrawForm(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("withdrawForm", new WithdrawForm());
        return withdrawPage(me, model);
    }

    @PostMapping("/withdraw")
    public String withdraw(@AuthenticationPrincipal AppUserDetails me,
                           @Valid @ModelAttribute("withdrawForm") WithdrawForm form,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirect) {
        BigDecimal balance = walletService.balanceOf(me.getId());
        if (form.getAmount() != null && !result.hasFieldErrors("amount") && form.getAmount().compareTo(balance) > 0) {
            result.rejectValue("amount", "amount.tooHigh", "You can withdraw at most " + euros(balance) + ".");
        }
        if (!result.hasErrors()) {
            try {
                walletService.withdraw(me.getId(), form.getAmount());
                redirect.addFlashAttribute(FLASH_SUCCESS, euros(form.getAmount())
                        + " is on its way to the account ending in " + PaymentChecks.ibanTail(form.getIban())
                        + " (simulated transfer).");
                return "redirect:/wallet";
            } catch (BusinessRuleException e) {
                result.reject("withdraw.failed", e.getMessage());
            }
        }
        return withdrawPage(me, model);
    }

    private String withdrawPage(AppUserDetails me, Model model) {
        model.addAttribute("balance", walletService.balanceOf(me.getId()));
        model.addAttribute("minWithdraw", WalletService.WITHDRAW_MIN);
        return "wallet/withdraw";
    }

    // ================================================================ helpers

    /** "€1,234.50", the same format as the templates. */
    static String euros(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "€%,.2f", amount);
    }

    /** Parses the optional {@code ?amount=} prefill; anything odd is ignored. */
    static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank() || value.length() > 12) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.trim().replace(',', '.'));
            return amount.signum() > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
