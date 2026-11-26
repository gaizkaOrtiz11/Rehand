package com.rehand.dto;

/** An active search filter shown as a removable chip: label and the URL of the same search without it. */
public class FilterChip {

    private final String label;
    private final String removeUrl;

    public FilterChip(String label, String removeUrl) {
        this.label = label;
        this.removeUrl = removeUrl;
    }

    public String getLabel() {
        return label;
    }

    public String getRemoveUrl() {
        return removeUrl;
    }
}
