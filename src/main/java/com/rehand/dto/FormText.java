package com.rehand.dto;

/** Small helpers used by the form objects' setters. */
final class FormText {

    private FormText() {
    }

    /** Trims the text; null, empty and blank text all become null. */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Keeps the text as typed (passwords may contain spaces); an empty string becomes null. */
    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
