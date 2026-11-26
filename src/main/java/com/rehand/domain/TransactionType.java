package com.rehand.domain;

/** Stored as text via {@code @Enumerated(EnumType.STRING)}; the matching CHECK constraint in schema.sql lists the same values. */
public enum TransactionType {
    TOP_UP,
    PAYMENT,
    REFUND,
    SALE_CREDIT,
    WITHDRAWAL
}
