package com.rehand.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.Offer;
import com.rehand.domain.OfferStatus;
import com.rehand.domain.Party;
import com.rehand.domain.User;

/**
 * The negotiation page: the whole chain from the buyer's first offer to the latest counter (listing,
 * seller, buyer and photos loaded), who is looking and which actions that person may take now.
 * The template only reads flags; the service re-checks every action.
 */
public class NegotiationView {

    /** One entry of the timeline. */
    public static final class Entry {
        private final Offer offer;
        private final OfferStatus displayStatus;
        private final boolean mine;
        private final boolean latest;

        Entry(Offer offer, OfferStatus displayStatus, boolean mine, boolean latest) {
            this.offer = offer;
            this.displayStatus = displayStatus;
            this.mine = mine;
            this.latest = latest;
        }

        public Offer getOffer() {
            return offer;
        }

        public OfferStatus getDisplayStatus() {
            return displayStatus;
        }

        public String getBadge() {
            return displayStatus.name().toLowerCase(Locale.ROOT);
        }

        /** Made by the viewer. */
        public boolean isMine() {
            return mine;
        }

        public boolean isLatest() {
            return latest;
        }

        /** "Offer" for the first entry, "Counter-offer" for the others. */
        public String getKind() {
            return offer.getParent() == null ? "Offer" : "Counter-offer";
        }

        /** "You" for the viewer's own entries, otherwise the username of the member who made it. */
        public String getMakerName() {
            return mine ? "You" : offer.getMaker().getUsername();
        }

        public String getMakerInitials() {
            return Initials.of(offer.getMaker().getUsername());
        }

        /** "offered" for the first entry, "countered with" for the others. */
        public String getVerb() {
            return offer.getParent() == null ? "offered" : "countered with";
        }

        /** "Buyer" or "Seller": the role of the member who made it. */
        public String getRole() {
            return offer.getMadeBy() == Party.BUYER ? "Buyer" : "Seller";
        }
    }

    private final List<Offer> chain;
    private final Offer latest;
    private final Party viewerParty;
    private final boolean admin;
    private final Long orderId;
    private final LocalDateTime now;

    /**
     * @param chain   offers from the first to the latest (at least one)
     * @param orderId the order placed with the latest offer, or null
     */
    public NegotiationView(List<Offer> chain, Long viewerId, boolean admin, Long orderId, LocalDateTime now) {
        this.chain = List.copyOf(chain);
        this.latest = chain.get(chain.size() - 1);
        this.viewerParty = latest.partyOf(viewerId);
        this.admin = admin;
        this.orderId = orderId;
        this.now = now;
    }

    public Offer getLatest() {
        return latest;
    }

    public Listing getListing() {
        return latest.getListing();
    }

    public List<Entry> getEntries() {
        List<Entry> out = new ArrayList<>();
        for (Offer o : chain) {
            boolean isLatest = o == latest;
            OfferStatus shown = isLatest ? getDisplayStatus() : o.getStatus();
            out.add(new Entry(o, shown, viewerParty != null && o.getMadeBy() == viewerParty, isLatest));
        }
        return out;
    }

    public OfferStatus getDisplayStatus() {
        return OfferRow.displayStatus(latest, orderId != null, now);
    }

    public String getBadge() {
        return getDisplayStatus().name().toLowerCase(Locale.ROOT);
    }

    public boolean isBuyer() {
        return viewerParty == Party.BUYER;
    }

    public boolean isSeller() {
        return viewerParty == Party.SELLER;
    }

    /** An admin who is not a party: read only. */
    public boolean isAdminView() {
        return viewerParty == null && admin;
    }

    /** The other side for a party; for an admin the buyer. */
    public User getCounterpart() {
        return viewerParty == Party.BUYER ? latest.getSeller() : latest.getBuyer();
    }

    public String getCounterpartRole() {
        return viewerParty == Party.BUYER ? "Seller" : "Buyer";
    }

    public String getCounterpartInitials() {
        return Initials.of(getCounterpart().getUsername());
    }

    private boolean open() {
        return getDisplayStatus() == OfferStatus.PENDING;
    }

    /** The viewer must answer the latest offer. */
    public boolean isCanRespond() {
        return open() && viewerParty != null && latest.responder() == viewerParty;
    }

    /** Accept needs the listing to be still for sale. */
    public boolean isCanAccept() {
        return isCanRespond() && getListing().getStatus() == ListingStatus.ACTIVE;
    }

    public boolean isCanCounter() {
        return isCanAccept() && getMaxCounter().signum() > 0;
    }

    public boolean isCanWithdraw() {
        return open() && viewerParty != null && latest.getMadeBy() == viewerParty;
    }

    /** The buyer may check out an accepted, unexpired, unused offer. */
    public boolean isCanCheckout() {
        return isBuyer() && getDisplayStatus() == OfferStatus.ACCEPTED && orderId == null;
    }

    public boolean isHasActions() {
        return isCanRespond() || isCanWithdraw() || isCanCheckout();
    }

    public BigDecimal getMaxCounter() {
        return getListing().getPrice().subtract(new BigDecimal("0.01"));
    }

    public Long getOrderId() {
        return orderId;
    }

    /** The order link is for the buyer and the seller of that order. */
    public boolean isShowOrderLink() {
        return orderId != null && viewerParty != null;
    }

    /** Deadline label of the latest offer while something is still expected. */
    public String getTimeLeft() {
        OfferStatus s = getDisplayStatus();
        return s == OfferStatus.PENDING || (s == OfferStatus.ACCEPTED && orderId == null)
                ? TimeLeft.label(now, latest.getExpiresAt()) : null;
    }

    /** Short sentence under the title, written for the viewer. */
    public String getSummary() {
        OfferStatus s = getDisplayStatus();
        String responder = latest.userOf(latest.responder()).getUsername();
        switch (s) {
            case PENDING:
                if (isCanRespond()) {
                    return "It is your turn: accept, decline or make a counter-offer.";
                }
                return "Waiting for " + responder + " to answer.";
            case ACCEPTED:
                if (orderId != null) {
                    return "Accepted and bought at the agreed price.";
                }
                return isBuyer() ? "Accepted. The item is reserved for you until you check out."
                        : "Accepted. The item is reserved until the buyer checks out.";
            case REJECTED:
                return "Declined. This negotiation is closed.";
            case WITHDRAWN:
                return "Withdrawn. This negotiation is closed.";
            case EXPIRED:
                return "Expired without a deal. This negotiation is closed.";
            default:
                return "";
        }
    }

    public boolean isOpen() {
        return open() || isCanCheckout();
    }
}
