package com.rehand.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * A purchase (table {@code orders}). Status flow:
 * PAID to SHIPPED to DELIVERED to COMPLETED, or PAID to CANCELLED. Each transition is a method
 * that throws {@link BusinessRuleException} when it is not allowed, so nothing illegal is ever saved,
 * and each one stamps its own timestamp column.
 *
 * Money: the price is debited from the buyer when the order is placed and stays "on the order"
 * (no escrow table) until COMPLETED credits the seller or CANCELLED refunds the buyer.
 *
 * {@code offerId} is a plain column for now; step 6 (offers) may turn it into a relation.
 *
 * The JPQL entity name is {@code PurchaseOrder}: ORDER is a JPQL keyword, so queries say
 * {@code from PurchaseOrder o} instead of {@code from Order o} (the table stays {@code orders}).
 */
@Entity(name = "PurchaseOrder")
@Table(name = "orders")
public class Order {

    public static final int TRACKING_MIN = 4;
    public static final int TRACKING_MAX = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "ship_name", nullable = false, length = 100)),
        @AttributeOverride(name = "street", column = @Column(name = "ship_street", nullable = false, length = 200)),
        @AttributeOverride(name = "city", column = @Column(name = "ship_city", nullable = false, length = 80)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "ship_postal_code", nullable = false, length = 12))
    })
    private ShippingAddress shipping;

    @Column(name = "tracking_code", length = 60)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 12)
    private OrderStatus status = OrderStatus.PAID;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected Order() {
        // required by JPA
    }

    /** A new PAID order. The caller has already checked that the buyer may buy the listing. */
    public Order(Listing listing, User buyer, BigDecimal price, ShippingAddress shipping, Long offerId) {
        this.listing = Objects.requireNonNull(listing, "listing");
        this.buyer = Objects.requireNonNull(buyer, "buyer");
        this.price = Objects.requireNonNull(price, "price");
        this.shipping = Objects.requireNonNull(shipping, "shipping");
        this.offerId = offerId;
        this.status = OrderStatus.PAID;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ------------------------------------------------------------ state machine

    /** PAID to SHIPPED, with a tracking code of 4 to 60 characters. */
    public void ship(String code) {
        if (status != OrderStatus.PAID) {
            throw new BusinessRuleException("Only a paid order can be marked as shipped.");
        }
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.length() < TRACKING_MIN || trimmed.length() > TRACKING_MAX) {
            throw new BusinessRuleException("Enter a tracking code of " + TRACKING_MIN + " to " + TRACKING_MAX + " characters.");
        }
        this.trackingCode = trimmed;
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = LocalDateTime.now();
    }

    /** SHIPPED to DELIVERED (the buyer received the parcel). */
    public void confirmDelivery() {
        if (status != OrderStatus.SHIPPED) {
            throw new BusinessRuleException("Delivery can only be confirmed for a shipped order.");
        }
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /** DELIVERED to COMPLETED (buyer clicked "All good" or the scheduler ran). The seller gets paid. */
    public void complete() {
        if (status != OrderStatus.DELIVERED) {
            throw new BusinessRuleException("Only a delivered order can be completed.");
        }
        this.status = OrderStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /** PAID to CANCELLED. Once the parcel is on its way the order cannot be cancelled. */
    public void cancel() {
        if (status != OrderStatus.PAID) {
            throw new BusinessRuleException("An order can only be cancelled before it is shipped.");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    /** Reviews (step 8): only after COMPLETED and only by the two parties. */
    public boolean canBeReviewedBy(User user) {
        return status == OrderStatus.COMPLETED && user != null
                && (isBuyer(user.getId()) || isSeller(user.getId()));
    }

    // ------------------------------------------------------------ parties

    public boolean isBuyer(Long userId) {
        return userId != null && buyer != null && userId.equals(buyer.getId());
    }

    public boolean isSeller(Long userId) {
        return userId != null && listing != null && listing.isOwnedBy(userId);
    }

    public boolean isParty(Long userId) {
        return isBuyer(userId) || isSeller(userId);
    }

    /** The seller is always the owner of the listing. */
    public User getSeller() {
        return listing == null ? null : listing.getSeller();
    }

    /** The other side of the deal for the given user (the seller for the buyer, otherwise the buyer). */
    public User counterpartOf(Long userId) {
        return isBuyer(userId) ? getSeller() : buyer;
    }

    /** True while the order is not finished (PAID, SHIPPED or DELIVERED). */
    public boolean isOpen() {
        return status == OrderStatus.PAID || status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED;
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

    public Long getOfferId() {
        return offerId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ShippingAddress getShipping() {
        return shipping;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Order other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
