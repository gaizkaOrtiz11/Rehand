package com.rehand.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.rehand.domain.TransactionType;

/** Money in and money out of one period (the wallet page shows the current month), with totals per type. */
public class WalletSummary {

    private final Map<TransactionType, BigDecimal> totals;

    public WalletSummary(Map<TransactionType, BigDecimal> totals) {
        EnumMap<TransactionType, BigDecimal> copy = new EnumMap<>(TransactionType.class);
        for (TransactionType t : TransactionType.values()) {
            BigDecimal v = totals == null ? null : totals.get(t);
            copy.put(t, v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP));
        }
        this.totals = Collections.unmodifiableMap(copy);
    }

    public Map<TransactionType, BigDecimal> getTotals() {
        return totals;
    }

    public BigDecimal total(TransactionType type) {
        return totals.get(type);
    }

    /** Top-ups, refunds and sale credits. */
    public BigDecimal getMoneyIn() {
        return total(TransactionType.TOP_UP).add(total(TransactionType.REFUND)).add(total(TransactionType.SALE_CREDIT));
    }

    /** Payments and withdrawals. */
    public BigDecimal getMoneyOut() {
        return total(TransactionType.PAYMENT).add(total(TransactionType.WITHDRAWAL));
    }
}
