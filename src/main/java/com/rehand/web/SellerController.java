package com.rehand.web;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.dto.PageNav;
import com.rehand.dto.SellerPageView;
import com.rehand.service.AppUserDetails;
import com.rehand.service.ListingService;

/** The public seller page {@code /users/{id}} and the member's own {@code /my/listings}. */
@Controller
public class SellerController {

    /** Tabs of /my/listings in display order. */
    private static final List<ListingStatus> TABS = List.of(
            ListingStatus.ACTIVE, ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.HIDDEN);

    private final ListingService listingService;

    public SellerController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/users/{id:\\d+}")
    public String seller(@PathVariable Long id,
                         @RequestParam(name = "page", defaultValue = "1") int page,
                         @AuthenticationPrincipal AppUserDetails me,
                         Model model) {
        SellerPageView view = listingService.sellerPage(id, page - 1);
        Page<Listing> listings = view.getListings();
        model.addAttribute("view", view);
        model.addAttribute("isMe", me != null && id.equals(me.getId()));
        model.addAttribute("pageWindow", PageNav.window(listings.getNumber() + 1, listings.getTotalPages()));
        model.addAttribute("pageBase", "/users/" + id + "?");
        return "users/seller";
    }

    @GetMapping("/my/listings")
    public String mine(@RequestParam(name = "status", required = false) String status,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       @AuthenticationPrincipal AppUserDetails me,
                       Model model) {
        ListingStatus filter = parseStatus(status);
        Page<Listing> listings = listingService.myListings(me.getId(), filter, page - 1);
        model.addAttribute("listings", listings);
        model.addAttribute("filter", filter);
        model.addAttribute("tabs", TABS);
        model.addAttribute("pageWindow", PageNav.window(listings.getNumber() + 1, listings.getTotalPages()));
        model.addAttribute("pageBase", filter == null
                ? "/my/listings?" : "/my/listings?status=" + filter.name() + "&");
        return "listings/mine";
    }

    /** Known status of the tab filter, or null for "all". HIDDEN, SOLD, RESERVED and ACTIVE are accepted. */
    static ListingStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            ListingStatus s = ListingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return TABS.contains(s) ? s : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
