package com.rehand.dto;

import java.math.BigInteger;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format checks for the simulated payment fields: card number (Luhn), card expiry (MM/YY) and IBAN (mod-97).
 * Nothing here stores or logs the values; they are checked and thrown away.
 */
public final class PaymentChecks {

    private static final Pattern CARD_DIGITS = Pattern.compile("\\d{13,19}");
    private static final Pattern EXPIRY = Pattern.compile("(\\d{2})\\s*/\\s*(\\d{2})");
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{1,30}");

    /** Cards further in the future than this are not believable. */
    private static final int MAX_YEARS_AHEAD = 20;

    private PaymentChecks() {
    }

    /** Removes spaces (and other whitespace) people type between digit groups. */
    public static String compact(String value) {
        return value == null ? null : value.replaceAll("\\s+", "");
    }

    /** 13 to 19 digits (spaces allowed between them) that pass the Luhn checksum. */
    public static boolean isValidCardNumber(String value) {
        String digits = compact(value);
        if (digits == null || !CARD_DIGITS.matcher(digits).matches()) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Parses "MM/YY" (spaces around the slash allowed); null when the text is not a valid month. */
    public static YearMonth parseExpiry(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = EXPIRY.matcher(value.trim());
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(1));
        int year = 2000 + Integer.parseInt(m.group(2));
        if (month < 1 || month > 12) {
            return null;
        }
        return YearMonth.of(year, month);
    }

    /** A card is valid until the end of its expiry month, so the current month still counts. */
    public static boolean isValidExpiry(String value, YearMonth now) {
        YearMonth expiry = parseExpiry(value);
        return expiry != null && !expiry.isBefore(now) && !expiry.isAfter(now.plusYears(MAX_YEARS_AHEAD));
    }

    /** Upper-case IBAN without spaces, or null. */
    public static String normalizeIban(String value) {
        String compact = compact(value);
        return compact == null ? null : compact.toUpperCase(Locale.ROOT);
    }

    /**
     * Country code, two check digits and up to 30 letters or digits (spaces allowed), and the
     * ISO 13616 mod-97 checksum: move the first four characters to the end, replace letters by
     * 10..35 and the remainder of dividing by 97 must be 1.
     */
    public static boolean isValidIban(String value) {
        String iban = normalizeIban(value);
        if (iban == null || iban.length() < 5 || iban.length() > 34 || !IBAN.matcher(iban).matches()) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder(rearranged.length() * 2);
        for (int i = 0; i < rearranged.length(); i++) {
            numeric.append(Character.getNumericValue(rearranged.charAt(i)));
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /** Last four characters of an IBAN for messages ("account ending 1234"); never the full number. */
    public static String ibanTail(String value) {
        String iban = normalizeIban(value);
        if (iban == null || iban.length() < 4) {
            return "";
        }
        return iban.substring(iban.length() - 4);
    }
}
