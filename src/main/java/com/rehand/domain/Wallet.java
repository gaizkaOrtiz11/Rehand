package com.rehand.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.rehand.exception.BusinessRuleException;

/**
 * The simulated money account of one user (table {@code wallets}, one row per user).
 * Registration creates it with balance 0.00.
 *
 * <p>The balance only changes through {@link #debit} and {@link #credit}. Each call returns the matching
 * {@link WalletTransaction} (not yet saved), so the cached balance and the ledger always move together.
 * Callers must hold a row lock on the wallet ({@code WalletRepository.findByUserIdForUpdate}).</p>
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Wallet() {
        // required by JPA
    }

    public Wallet(User user, BigDecimal balance) {
        this.user = user;
        this.balance = balance;
    }

    // ------------------------------------------------------------------ money

    /** Message used when a debit would make the balance negative. */
    public static final String INSUFFICIENT_FUNDS = "Your wallet balance is too low.";

    /** True when the balance covers {@code amount} (null counts as zero). */
    public boolean hasFunds(BigDecimal amount) {
        BigDecimal needed = amount == null ? BigDecimal.ZERO : amount;
        return currentBalance().compareTo(needed) >= 0;
    }

    /**
     * Takes {@code amount} out of the wallet.
     *
     * @return the ledger row for this change (not yet saved)
     * @throws IllegalArgumentException when the amount is null or not positive
     * @throws BusinessRuleException    when the balance is too low
     */
    public WalletTransaction debit(BigDecimal amount, TransactionType type, Order order) {
        BigDecimal value = requirePositive(amount);
        if (!hasFunds(value)) {
            throw new BusinessRuleException(INSUFFICIENT_FUNDS);
        }
        balance = currentBalance().subtract(value).setScale(2, RoundingMode.HALF_UP);
        updatedAt = LocalDateTime.now();
        return new WalletTransaction(this, order, type, value, balance);
    }

    /**
     * Adds {@code amount} to the wallet.
     *
     * @return the ledger row for this change (not yet saved)
     * @throws IllegalArgumentException when the amount is null or not positive
     */
    public WalletTransaction credit(BigDecimal amount, TransactionType type, Order order) {
        BigDecimal value = requirePositive(amount);
        balance = currentBalance().add(value).setScale(2, RoundingMode.HALF_UP);
        updatedAt = LocalDateTime.now();
        return new WalletTransaction(this, order, type, value, balance);
    }

    private BigDecimal currentBalance() {
        return balance == null ? BigDecimal.ZERO.setScale(2) : balance;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Wallet other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
