package com.rehand.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.Offer;
import com.rehand.domain.Party;

/**
 * The offer part of the listing page, from the viewer's point of view:
 * <ul>
 *   <li>owner: how many offers are open ({@link #getOpenOfferCount()});</li>
 *   <li>a member with an open negotiation on this listing: its latest offer ({@link #getOffer()}) and what
 *       they may do with it;</li>
 *   <li>any other logged-in member on an ACTIVE listing: may make an offer below the asking price.</li>
 * </ul>
 * Guests get {@link #anonymous()} (nothing to show). The service re-checks every action.
 */
public class OfferPanel {

    private final boolean loggedIn;
    private final boolean owner;
    private final ListingStatus listingStatus;
    private final BigDecimal askingPrice;
    private final long openOfferCount;
    private final Offer offer;
    private final LocalDateTime now;

    public OfferPanel(boolean loggedIn, boolean owner, Listing listing, long openOfferCount,
                      Offer offer, LocalDateTime now) {
        this.loggedIn = loggedIn;
        this.owner = owner;
        this.listingStatus = listing == null ? null : listing.getStatus();
        this.askingPrice = listing == null ? null : listing.getPrice();
        this.openOfferCount = openOfferCount;
        this.offer = offer;
        this.now = now;
    }

    public static OfferPanel anonymous() {
        return new OfferPanel(false, false, null, 0, null, LocalDateTime.now());
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isOwner() {
        return owner;
    }

    public long getOpenOfferCount() {
        return openOfferCount;
    }

    /** Latest offer of the viewer's open negotiation (PENDING, or ACCEPTED and not yet used), or null. */
    public Offer getOffer() {
        return offer;
    }

    public boolean isHasNegotiation() {
        return offer != null;
    }

    /** Logged in, not the owner, listing ACTIVE and no open negotiation. */
    public boolean isCanMakeOffer() {
        return loggedIn && !owner && offer == null && listingStatus == ListingStatus.ACTIVE
                && askingPrice != null && askingPrice.compareTo(new BigDecimal("0.02")) >= 0;
    }

    /** Highest amount an offer may have: the asking price minus one cent. */
    public BigDecimal getMaxAmount() {
        return askingPrice == null ? BigDecimal.ZERO : askingPrice.subtract(new BigDecimal("0.01"));
    }

    public BigDecimal getAskingPrice() {
        return askingPrice;
    }

    /** The viewer's own offer is waiting for the seller. */
    public boolean isWaitingForSeller() {
        return offer != null && offer.isPending() && offer.getMadeBy() == Party.BUYER;
    }

    /** The seller countered: the viewer (the buyer) must answer. */
    public boolean isCounterForMe() {
        return offer != null && offer.isPending() && offer.getMadeBy() == Party.SELLER;
    }

    /** The seller accepted: the viewer may check out at the agreed price until the deadline. */
    public boolean isAccepted() {
        return offer != null && offer.isAccepted();
    }

    /** Counter-offers must be below the asking price and different from the current amount. */
    public BigDecimal getMaxCounter() {
        return getMaxAmount();
    }

    public String getTimeLeft() {
        return offer == null ? "" : TimeLeft.label(now, offer.getExpiresAt());
    }
}
