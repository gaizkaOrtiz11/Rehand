package com.rehand.dto;

import java.util.Locale;

/** Two-letter initials of a username for avatars without a photo, for example "ausra_k" gives "AK". */
public final class Initials {

    private Initials() {
    }

    public static String of(String username) {
        if (username == null || username.isBlank()) {
            return "?";
        }
        String[] parts = username.trim().split("[^\\p{L}\\p{N}]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && sb.length() < 2) {
                sb.appendCodePoint(part.codePointAt(0));
            }
        }
        if (sb.length() == 0) {
            return "?";
        }
        if (sb.length() == 1 && parts.length == 1 && parts[0].length() > 1) {
            sb.appendCodePoint(parts[0].codePointAt(1));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
