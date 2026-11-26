package com.rehand.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.rehand.exception.BusinessRuleException;

/**
 * One offer or counter-offer (table {@code offers}). A negotiation is a chain: the buyer's first offer,
 * then each counter points at the offer it answers through {@code parent}. {@code buyer} is always the
 * buyer of the negotiation, also on the seller's counters; the seller is the owner of the listing.
 *
 * Status flow of one offer: PENDING to ACCEPTED, REJECTED, COUNTERED (a new PENDING offer from the other
 * party replaces it), WITHDRAWN (by the party who made it) or EXPIRED (scheduler). Each transition is a
 * method that throws {@link BusinessRuleException} when it is not allowed.
 *
 * {@code expiresAt}: for a PENDING offer the deadline to answer (48 h); once ACCEPTED, the deadline for the
 * buyer to check out (24 h after the acceptance).
 */
@Entity
@Table(name = "offers")
public class Offer {

    public static final int ANSWER_HOURS = 48;
    public static final int CHECKOUT_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_offer_id")
    private Offer parent;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "made_by", nullable = false, length = 10)
    private Party madeBy;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 10)
    private OfferStatus status = OfferStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected Offer() {
        // required by JPA
    }

    /** A new PENDING offer made by {@code madeBy}, answerable for 48 hours from {@code now}. */
    public Offer(Listing listing, User buyer, Offer parent, Party madeBy, BigDecimal amount, LocalDateTime now) {
        this.listing = Objects.requireNonNull(listing, "listing");
        this.buyer = Objects.requireNonNull(buyer, "buyer");
        this.parent = parent;
        this.madeBy = Objects.requireNonNull(madeBy, "madeBy");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = OfferStatus.PENDING;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.expiresAt = now.plusHours(ANSWER_HOURS);
    }

    /** The buyer's first offer on a listing. */
    public static Offer fromBuyer(Listing listing, User buyer, BigDecimal amount, LocalDateTime now) {
        return new Offer(listing, buyer, null, Party.BUYER, amount, now);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(ANSWER_HOURS);
        }
    }

    // ------------------------------------------------------------ state machine

    /** PENDING and not expired to ACCEPTED. The buyer then has 24 hours to check out at this amount. */
    public void accept(LocalDateTime now) {
        requireAnswerable(now);
        status = OfferStatus.ACCEPTED;
        expiresAt = now.plusHours(CHECKOUT_HOURS);
    }

    /** PENDING to REJECTED (the responder said no, or the item was sold, reserved or taken down). */
    public void reject() {
        if (status != OfferStatus.PENDING) {
            throw new BusinessRuleException("Only an open offer can be declined.");
        }
        status = OfferStatus.REJECTED;
    }

    /**
     * PENDING and not expired to COUNTERED. Returns the new PENDING offer from the other party
     * (same listing and buyer, parent = this), answerable for 48 hours.
     */
    public Offer counter(BigDecimal newAmount, LocalDateTime now) {
        requireAnswerable(now);
        if (newAmount == null || newAmount.signum() <= 0) {
            throw new BusinessRuleException("Enter an amount above zero.");
        }
        if (newAmount.compareTo(amount) == 0) {
            throw new BusinessRuleException("A counter-offer needs a different amount.");
        }
        status = OfferStatus.COUNTERED;
        return new Offer(listing, buyer, this, responder(), newAmount, now);
    }

    /** PENDING to WITHDRAWN. Only the party who made the offer may withdraw it (checked by the caller). */
    public void withdraw() {
        if (status != OfferStatus.PENDING) {
            throw new BusinessRuleException("Only an open offer can be withdrawn.");
        }
        status = OfferStatus.WITHDRAWN;
    }

    /** PENDING or ACCEPTED to EXPIRED (scheduler). */
    public void expire() {
        if (status != OfferStatus.PENDING && status != OfferStatus.ACCEPTED) {
            throw new BusinessRuleException("Only an open or accepted offer can expire.");
        }
        status = OfferStatus.EXPIRED;
    }

    /** True when the deadline has passed (to answer for PENDING, to check out for ACCEPTED). */
    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now != null && !now.isBefore(expiresAt);
    }

    private void requireAnswerable(LocalDateTime now) {
        if (status != OfferStatus.PENDING) {
            throw new BusinessRuleException("This offer has already been answered.");
        }
        if (isExpired(now)) {
            throw new BusinessRuleException("This offer has expired.");
        }
    }

    // ------------------------------------------------------------ helpers

    public boolean isPending() {
        return status == OfferStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == OfferStatus.ACCEPTED;
    }

    /** The party who must answer this offer: the opposite of the one who made it. */
    public Party responder() {
        return madeBy == Party.BUYER ? Party.SELLER : Party.BUYER;
    }

    public boolean isBuyer(Long userId) {
        return userId != null && buyer != null && userId.equals(buyer.getId());
    }

    public boolean isSeller(Long userId) {
        return userId != null && listing != null && listing.isOwnedBy(userId);
    }

    public boolean isParty(Long userId) {
        return isBuyer(userId) || isSeller(userId);
    }

    /** The party this user plays in the negotiation, or null for anyone else. */
    public Party partyOf(Long userId) {
        if (isBuyer(userId)) {
            return Party.BUYER;
        }
        return isSeller(userId) ? Party.SELLER : null;
    }

    public User getSeller() {
        return listing == null ? null : listing.getSeller();
    }

    /** The user who plays the given party. */
    public User userOf(Party party) {
        return party == Party.BUYER ? buyer : getSeller();
    }

    /** The user who made this offer. */
    public User getMaker() {
        return userOf(madeBy);
    }

    // ------------------------------------------------------------ getters

    public Long getId() {
        return id;
    }

    public Listing getListing() {
        return listing;
    }

    public User getBuyer() {
        return buyer;
    }

    public Offer getParent() {
        return parent;
    }

    public Party getMadeBy() {
        return madeBy;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Offer other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
