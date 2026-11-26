package com.rehand.web;

import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.Order;
import com.rehand.domain.OrderStatus;
import com.rehand.domain.User;
import com.rehand.dto.CancelForm;
import com.rehand.dto.CheckoutForm;
import com.rehand.dto.CheckoutView;
import com.rehand.dto.OrderView;
import com.rehand.dto.PageNav;
import com.rehand.dto.ShipForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.OrderService;

/**
 * Checkout (buy now, or at the agreed price of an accepted offer with {@code ?offer=id}) and the order pages: list of purchases and sales, order detail and the four
 * follow-up actions. All routes need a login (SecurityConfig: anyRequest().authenticated()); who may
 * do what is checked again in {@link OrderService}. Every POST redirects afterwards, except when a
 * form has validation errors: then the page is shown again with the typed values and the messages.
 */
@Controller
public class OrderController {

    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String FLASH_ERROR = "flashError";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ================================================================ checkout

    @GetMapping("/checkout/{listingId:\\d+}")
    public String checkoutForm(@PathVariable Long listingId,
                               @RequestParam(name = "offer", required = false) Long offerId,
                               @AuthenticationPrincipal AppUserDetails me,
                               Model model, RedirectAttributes redirect) {
        CheckoutView view;
        try {
            view = orderService.checkoutView(me.getId(), listingId, offerId);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/listings/" + listingId;
        }
        User buyer = view.getBuyer();
        CheckoutForm form = new CheckoutForm();
        form.setShipName(buyer.getUsername());
        form.setShipCity(buyer.getCity());
        form.setOfferId(view.getOfferId());
        model.addAttribute("checkoutForm", form);
        model.addAttribute("checkout", view);
        return "orders/checkout";
    }

    @PostMapping("/checkout/{listingId:\\d+}")
    public String checkout(@PathVariable Long listingId,
                           @Valid @ModelAttribute("checkoutForm") CheckoutForm form,
                           BindingResult binding,
                           @AuthenticationPrincipal AppUserDetails me,
                           Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                Order order = orderService.placeOrder(me.getId(), listingId, form);
                redirect.addFlashAttribute(FLASH_SUCCESS,
                        "Payment done. The seller has been told to ship your item.");
                return "redirect:/orders/" + order.getId();
            } catch (BusinessRuleException e) {
                model.addAttribute(FLASH_ERROR, e.getMessage());
            }
        }
        try {
            model.addAttribute("checkout", orderService.checkoutView(me.getId(), listingId, form.getOfferId()));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/listings/" + listingId;
        }
        return "orders/checkout";
    }

    // ================================================================ lists

    @GetMapping("/orders")
    public String list(@RequestParam(name = "tab", required = false) String tab,
                       @RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "page", required = false, defaultValue = "1") int page,
                       @AuthenticationPrincipal AppUserDetails me,
                       Model model) {
        boolean sales = "sales".equals(tab);
        OrderStatus filter = parseStatus(status);
        int pageIndex = Math.max(page, 1) - 1;

        Page<Order> orders = sales
                ? orderService.sales(me.getId(), filter, pageIndex)
                : orderService.purchases(me.getId(), filter, pageIndex);
        if (orders.getTotalPages() > 0 && pageIndex >= orders.getTotalPages()) {
            pageIndex = orders.getTotalPages() - 1;
            orders = sales
                    ? orderService.sales(me.getId(), filter, pageIndex)
                    : orderService.purchases(me.getId(), filter, pageIndex);
        }

        String tabName = sales ? "sales" : "purchases";
        long[] open = orderService.openCounts(me.getId());
        model.addAttribute("tab", tabName);
        model.addAttribute("sales", sales);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", List.of(OrderStatus.values()));
        model.addAttribute("orders", orders);
        model.addAttribute("openPurchases", open[0]);
        model.addAttribute("openSales", open[1]);
        model.addAttribute("meId", me.getId());
        model.addAttribute("pageWindow", PageNav.window(orders.getNumber() + 1, orders.getTotalPages()));
        model.addAttribute("pageBase", "/orders?tab=" + tabName + "&"
                + (filter == null ? "" : "status=" + filter.name() + "&"));
        return "orders/list";
    }

    // ================================================================ detail

    @GetMapping("/orders/{id:\\d+}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        if (!model.containsAttribute("shipForm")) {
            model.addAttribute("shipForm", new ShipForm());
        }
        if (!model.containsAttribute("cancelForm")) {
            model.addAttribute("cancelForm", new CancelForm());
        }
        model.addAttribute("view", orderService.view(me.getId(), id));
        return "orders/detail";
    }

    // ================================================================ actions

    @PostMapping("/orders/{id:\\d+}/ship")
    public String ship(@PathVariable Long id,
                       @Valid @ModelAttribute("shipForm") ShipForm form,
                       BindingResult binding,
                       @AuthenticationPrincipal AppUserDetails me,
                       Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return detail(id, me, model);
        }
        try {
            orderService.markShipped(me.getId(), id, form.getTrackingCode());
            redirect.addFlashAttribute(FLASH_SUCCESS, "Marked as shipped. The buyer can now follow the parcel.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id:\\d+}/deliver")
    public String confirmDelivery(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                                  RedirectAttributes redirect) {
        try {
            orderService.confirmDelivery(me.getId(), id);
            redirect.addFlashAttribute(FLASH_SUCCESS,
                    "Delivery confirmed. Check the item, then release the payment to the seller.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id:\\d+}/complete")
    public String complete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                           RedirectAttributes redirect) {
        try {
            orderService.complete(me.getId(), id);
            redirect.addFlashAttribute(FLASH_SUCCESS, "Thanks. The payment has been released to the seller.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id:\\d+}/cancel")
    public String cancel(@PathVariable Long id,
                         @Valid @ModelAttribute("cancelForm") CancelForm form,
                         BindingResult binding,
                         @AuthenticationPrincipal AppUserDetails me,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("cancelOpen", Boolean.TRUE);
            return detail(id, me, model);
        }
        try {
            orderService.cancel(me.getId(), id, form.getReason());
            redirect.addFlashAttribute(FLASH_SUCCESS, "The order was cancelled and the buyer was refunded in full.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    // ================================================================ helpers

    private static OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
