package com.rehand.dto;

import org.springframework.data.domain.Page;

import com.rehand.domain.Listing;
import com.rehand.domain.User;

/** Data of the public seller page: the member, one page of their available listings and the total. */
public class SellerPageView {

    private final User seller;
    private final Page<Listing> listings;
    private final long activeCount;

    public SellerPageView(User seller, Page<Listing> listings, long activeCount) {
        this.seller = seller;
        this.listings = listings;
        this.activeCount = activeCount;
    }

    public User getSeller() {
        return seller;
    }

    public Page<Listing> getListings() {
        return listings;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public String getInitials() {
        return Initials.of(seller.getUsername());
    }
}
