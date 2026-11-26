package com.rehand.web;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.Offer;
import com.rehand.dto.OfferFilter;
import com.rehand.dto.OfferForm;
import com.rehand.dto.OfferRow;
import com.rehand.dto.PageNav;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.OfferService;

/**
 * Offers: make an offer from the listing page, the offers list (received and sent), the negotiation page
 * and the four answers (accept, decline, counter, withdraw). All routes need a login
 * (SecurityConfig: anyRequest().authenticated()); who may do what is checked again in {@link OfferService}.
 * Every POST redirects afterwards; a rule or validation problem comes back as a flash message.
 */
@Controller
public class OfferController {

    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String FLASH_ERROR = "flashError";

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    // ================================================================ make an offer

    @PostMapping("/listings/{id:\\d+}/offers")
    public String make(@PathVariable Long id,
                       @Valid @ModelAttribute("offerForm") OfferForm form,
                       BindingResult binding,
                       @AuthenticationPrincipal AppUserDetails me,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute(FLASH_ERROR, firstError(binding));
            return "redirect:/listings/" + id;
        }
        try {
            Offer offer = offerService.makeOffer(me.getId(), id, form.getAmount());
            redirect.addFlashAttribute(FLASH_SUCCESS, "Offer sent. The seller has " + Offer.ANSWER_HOURS
                    + " hours to answer.");
            return "redirect:/offers/" + offer.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/listings/" + id;
        }
    }

    // ================================================================ list

    @GetMapping("/offers")
    public String list(@RequestParam(name = "tab", required = false) String tab,
                       @RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "page", required = false, defaultValue = "1") int page,
                       @AuthenticationPrincipal AppUserDetails me,
                       Model model) {
        boolean sent = "sent".equals(tab);
        OfferFilter filter = OfferFilter.parse(status);
        int pageIndex = Math.max(page, 1) - 1;

        Page<OfferRow> rows = offerService.list(me.getId(), !sent, filter, pageIndex);
        if (rows.getTotalPages() > 0 && pageIndex >= rows.getTotalPages()) {
            pageIndex = rows.getTotalPages() - 1;
            rows = offerService.list(me.getId(), !sent, filter, pageIndex);
        }

        String tabName = sent ? "sent" : "received";
        long[] waiting = offerService.waitingCounts(me.getId());
        model.addAttribute("tab", tabName);
        model.addAttribute("sent", sent);
        model.addAttribute("filter", filter);
        model.addAttribute("filters", OfferFilter.values());
        model.addAttribute("rows", rows);
        model.addAttribute("waitingReceived", waiting[0]);
        model.addAttribute("waitingSent", waiting[1]);
        model.addAttribute("pageWindow", PageNav.window(rows.getNumber() + 1, rows.getTotalPages()));
        model.addAttribute("pageBase", "/offers?tab=" + tabName + "&"
                + (filter == OfferFilter.ALL ? "" : "status=" + filter.getParam() + "&"));
        return "offers/list";
    }

    // ================================================================ negotiation page

    @GetMapping("/offers/{id:\\d+}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("view", offerService.view(me.getId(), id));
        return "offers/detail";
    }

    // ================================================================ answers

    @PostMapping("/offers/{id:\\d+}/accept")
    public String accept(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        try {
            Offer offer = offerService.accept(me.getId(), id);
            redirect.addFlashAttribute(FLASH_SUCCESS, offer.isBuyer(me.getId())
                    ? "Deal. The item is reserved for you: check out within " + Offer.CHECKOUT_HOURS + " hours."
                    : "Offer accepted. The item is reserved for the buyer for " + Offer.CHECKOUT_HOURS + " hours.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/offers/" + id;
    }

    @PostMapping("/offers/{id:\\d+}/reject")
    public String reject(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        try {
            offerService.reject(me.getId(), id);
            redirect.addFlashAttribute(FLASH_SUCCESS, "Offer declined. The other member has been told.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/offers/" + id;
    }

    @PostMapping("/offers/{id:\\d+}/counter")
    public String counter(@PathVariable Long id,
                          @Valid @ModelAttribute("offerForm") OfferForm form,
                          BindingResult binding,
                          @AuthenticationPrincipal AppUserDetails me,
                          RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute(FLASH_ERROR, firstError(binding));
            return "redirect:/offers/" + id;
        }
        try {
            Offer next = offerService.counter(me.getId(), id, form.getAmount());
            redirect.addFlashAttribute(FLASH_SUCCESS, "Counter-offer sent. The other member has "
                    + Offer.ANSWER_HOURS + " hours to answer.");
            return "redirect:/offers/" + next.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/offers/" + id;
        }
    }

    @PostMapping("/offers/{id:\\d+}/withdraw")
    public String withdraw(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                           RedirectAttributes redirect) {
        try {
            offerService.withdraw(me.getId(), id);
            redirect.addFlashAttribute(FLASH_SUCCESS, "Offer withdrawn.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/offers/" + id;
    }

    // ================================================================ helpers

    /** The first validation message; a value that is not a number gets a readable message too. */
    static String firstError(BindingResult binding) {
        FieldError error = binding.getFieldError("amount");
        if (error == null) {
            return "Check the amount and try again.";
        }
        if (error.isBindingFailure()) {
            return "Enter the amount as a number in euros, for example 25.50.";
        }
        return error.getDefaultMessage();
    }
}
