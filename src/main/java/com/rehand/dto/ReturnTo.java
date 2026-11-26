package com.rehand.dto;

/**
 * Checks the optional {@code returnTo} target of the top-up page, so it cannot be used as an open redirect.
 * Only local paths are accepted: they start with one "/" (not "//" and not "/\"), and contain no
 * whitespace, control characters, backslashes or braces (Spring would read braces in a
 * {@code redirect:} target as URI template variables).
 */
public final class ReturnTo {

    static final int MAX_LENGTH = 500;

    private ReturnTo() {
    }

    public static boolean isSafe(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        if (value.charAt(0) != '/' || value.startsWith("//")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '{' || c == '}' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /** The value when it is safe, otherwise null. */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return isSafe(trimmed) ? trimmed : null;
    }
}
