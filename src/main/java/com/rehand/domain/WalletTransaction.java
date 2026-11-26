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

/**
 * One row of the wallet ledger (table {@code wallet_transactions}). Rows are written once and never changed.
 * {@code amount} is always positive; the {@link TransactionType} says whether money came in or went out.
 * {@code order} is set for PAYMENT, REFUND and SALE_CREDIT and null for TOP_UP and WITHDRAWAL.
 * Instances are created by {@link Wallet#debit} and {@link Wallet#credit}.
 */
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * The same column read as a plain value (never written through this field), so the history page can link
     * to the order after the session is closed (open-in-view is off) without touching the lazy {@link #order}.
     */
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderIdValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 12)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WalletTransaction() {
        // required by JPA
    }

    WalletTransaction(Wallet wallet, Order order, TransactionType type, BigDecimal amount, BigDecimal balanceAfter) {
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.order = order;
        this.type = Objects.requireNonNull(type, "type");
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** True for TOP_UP, REFUND and SALE_CREDIT (money came in). */
    public boolean isCredit() {
        return type == TransactionType.TOP_UP || type == TransactionType.REFUND || type == TransactionType.SALE_CREDIT;
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Order getOrder() {
        return order;
    }

    /**
     * Id of the linked order or null. Rows read from the database use the plain column (safe outside a
     * transaction); a row created in this request falls back to the order object it was built with.
     */
    public Long getOrderId() {
        if (orderIdValue != null) {
            return orderIdValue;
        }
        return order == null ? null : order.getId();
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WalletTransaction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
