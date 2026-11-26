package com.rehand.domain;

/** Stored as text via {@code @Enumerated(EnumType.STRING)}; the matching CHECK constraint in schema.sql lists the same values. */
public enum NotificationType {
    NEW_MESSAGE,
    OFFER_RECEIVED,
    OFFER_ANSWERED,
    ITEM_SOLD,
    ORDER_STATUS,
    NEW_REVIEW,
    SELLER_POSTED,
    SEARCH_MATCH,
    LISTING_REMOVED
}
