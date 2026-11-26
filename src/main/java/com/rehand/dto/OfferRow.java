package com.rehand.dto;

import java.time.LocalDateTime;
import java.util.Locale;

import com.rehand.domain.Offer;
import com.rehand.domain.OfferStatus;
import com.rehand.domain.Party;
import com.rehand.domain.User;

/**
 * One row of the offers list: the latest offer of a negotiation seen by one member
 * (as seller in the Received tab, as buyer in the Sent tab).
 */
public class OfferRow {

    private final Offer offer;
    private final Party viewerParty;
    private final boolean used;
    private final LocalDateTime now;

    /**
     * @param used true when this (accepted) offer already became an order
     */
    public OfferRow(Offer offer, Party viewerParty, boolean used, LocalDateTime now) {
        this.offer = offer;
        this.viewerParty = viewerParty;
        this.used = used;
        this.now = now;
    }

    public Offer getOffer() {
        return offer;
    }

    /** The other side: the buyer for the seller, the seller for the buyer. */
    public User getCounterpart() {
        return viewerParty == Party.SELLER ? offer.getBuyer() : offer.getSeller();
    }

    public String getCounterpartRole() {
        return viewerParty == Party.SELLER ? "From" : "Seller";
    }

    /** The status to show: an open offer past its deadline shows as expired before the scheduler runs. */
    public OfferStatus getDisplayStatus() {
        return displayStatus(offer, used, now);
    }

    /** Lower-case status for the badge class ({@code badge--pending}). */
    public String getBadge() {
        return getDisplayStatus().name().toLowerCase(Locale.ROOT);
    }

    /** True while an answer is still expected from someone. */
    public boolean isAwaitingAnswer() {
        return getDisplayStatus() == OfferStatus.PENDING;
    }

    /** True when the viewer is the one who must answer. */
    public boolean isWaitingForMe() {
        return isAwaitingAnswer() && offer.responder() == viewerParty;
    }

    /** "You", or the username of the party who must answer. */
    public String getWaitingForLabel() {
        if (!isAwaitingAnswer()) {
            return null;
        }
        return isWaitingForMe() ? "You" : offer.userOf(offer.responder()).getUsername();
    }

    /** Accepted and still waiting for the buyer's checkout. */
    public boolean isAwaitingCheckout() {
        return getDisplayStatus() == OfferStatus.ACCEPTED && !used;
    }

    public boolean isUsed() {
        return used;
    }

    /** Deadline label for open and accepted offers, null for closed ones. */
    public String getTimeLeft() {
        return isAwaitingAnswer() || isAwaitingCheckout() ? TimeLeft.label(now, offer.getExpiresAt()) : null;
    }

    public boolean isMadeByMe() {
        return offer.getMadeBy() == viewerParty;
    }

    static OfferStatus displayStatus(Offer offer, boolean used, LocalDateTime now) {
        OfferStatus s = offer.getStatus();
        if ((s == OfferStatus.PENDING || (s == OfferStatus.ACCEPTED && !used)) && offer.isExpired(now)) {
            return OfferStatus.EXPIRED;
        }
        return s;
    }
}
