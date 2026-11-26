package com.rehand.domain;

/** Stored as text via {@code @Enumerated(EnumType.STRING)}; the matching CHECK constraint in schema.sql lists the same values. */
public enum ListingStatus {
    ACTIVE,
    RESERVED,
    SOLD,
    HIDDEN,
    REMOVED
}
