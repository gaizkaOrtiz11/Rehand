package com.rehand.dto;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;

import com.rehand.domain.OfferStatus;

/**
 * Status filter of the offers list (GET parameter {@code status}). It applies to the latest offer of
 * each negotiation: COUNTERED never appears there, because a countered offer always has a newer one.
 */
public enum OfferFilter {

    ALL("All", EnumSet.allOf(OfferStatus.class)),
    OPEN("Open", EnumSet.of(OfferStatus.PENDING)),
    ACCEPTED("Accepted", EnumSet.of(OfferStatus.ACCEPTED)),
    CLOSED("Closed", EnumSet.of(OfferStatus.REJECTED, OfferStatus.WITHDRAWN, OfferStatus.EXPIRED));

    private final String label;
    private final Collection<OfferStatus> statuses;

    OfferFilter(String label, Collection<OfferStatus> statuses) {
        this.label = label;
        this.statuses = statuses;
    }

    public String getLabel() {
        return label;
    }

    public Collection<OfferStatus> getStatuses() {
        return statuses;
    }

    /** Lower-case value used in URLs ({@code ?status=open}). */
    public String getParam() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses the GET parameter; anything unknown or empty means ALL. */
    public static OfferFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
