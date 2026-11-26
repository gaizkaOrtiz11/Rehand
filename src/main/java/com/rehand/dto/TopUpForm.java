package com.rehand.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Top-up form (POST /wallet/top-up). The amount is either a preset chip (10, 25, 50, 100) or the typed
 * {@link #customAmount}. {@link #getAmount()} resolves the two and carries the
 * range constraints, so errors are reported on the property {@code amount}.
 *
 * <p>The card fields are simulated: they are validated and then discarded. They are never stored,
 * never logged and never sent back to the browser ({@link #toString()} does not print them).</p>
 */
public class TopUpForm {

    public static final String MIN = "5.00";
    public static final String MAX = "1000.00";
    public static final String CUSTOM = "custom";
    public static final List<String> PRESETS = List.of("10", "25", "50", "100");
    public static final String DEFAULT_PRESET = "25";

    private String preset = DEFAULT_PRESET;

    @Digits(integer = 4, fraction = 2, message = "Use at most two decimals, for example 37.50.")
    private BigDecimal customAmount;

    @NotBlank(message = "Enter the name on the card.")
    @Size(max = 100, message = "Use at most 100 characters.")
    private String cardholderName;

    @NotBlank(message = "Enter the card number.")
    @Pattern(regexp = "[0-9 ]*", message = "Use digits only (spaces are fine).")
    @CardNumber
    private String cardNumber;

    @NotBlank(message = "Enter the expiry date.")
    @CardExpiry
    private String expiry;

    @NotBlank(message = "Enter the security code.")
    @Pattern(regexp = "\\d{3,4}", message = "The security code has 3 or 4 digits.")
    private String cvc;

    private String returnTo;

    /**
     * Form prefilled from {@code ?amount=} (for example the shortfall sent by the checkout page): rounded up to
     * cents and kept within 5.00 to 1000.00, then shown as a preset chip when it matches one, otherwise in the
     * custom field.
     */
    public static TopUpForm prefilled(BigDecimal requested, String returnTo) {
        TopUpForm form = new TopUpForm();
        form.setReturnTo(returnTo);
        if (requested != null && requested.signum() > 0) {
            BigDecimal amount = requested.setScale(2, RoundingMode.CEILING)
                    .max(new BigDecimal(MIN))
                    .min(new BigDecimal(MAX));
            String match = null;
            for (String p : PRESETS) {
                if (new BigDecimal(p).compareTo(amount) == 0) {
                    match = p;
                }
            }
            if (match != null) {
                form.preset = match;
            } else {
                form.preset = CUSTOM;
                form.customAmount = amount;
            }
        }
        return form;
    }

    /**
     * The amount to add. A typed custom amount wins (so the page also works without JavaScript, where
     * typing does not move the chip selection); otherwise the chosen preset chip. Null when nothing usable was chosen.
     */
    @NotNull(message = "Choose or enter an amount.")
    @DecimalMin(value = MIN, message = "The minimum top-up is 5.00 euros.")
    @DecimalMax(value = MAX, message = "The maximum top-up is 1000.00 euros.")
    @Digits(integer = 4, fraction = 2, message = "Use at most two decimals, for example 37.50.")
    public BigDecimal getAmount() {
        if (customAmount != null) {
            return customAmount;
        }
        if (preset != null && !CUSTOM.equals(preset)) {
            try {
                return new BigDecimal(preset);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** True when the "Other amount" chip should show as selected. */
    public boolean isCustom() {
        return CUSTOM.equals(preset) || customAmount != null;
    }

    /** Forgets the card number and security code, so a re-rendered page never contains them. */
    public void clearSensitive() {
        cardNumber = null;
        cvc = null;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = FormText.trimToNull(preset);
    }

    public BigDecimal getCustomAmount() {
        return customAmount;
    }

    public void setCustomAmount(BigDecimal customAmount) {
        this.customAmount = customAmount;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public void setCardholderName(String cardholderName) {
        this.cardholderName = FormText.trimToNull(cardholderName);
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = FormText.trimToNull(cardNumber);
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry(String expiry) {
        this.expiry = FormText.trimToNull(expiry);
    }

    public String getCvc() {
        return cvc;
    }

    public void setCvc(String cvc) {
        this.cvc = FormText.trimToNull(cvc);
    }

    public String getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = ReturnTo.sanitize(returnTo);
    }

    @Override
    public String toString() {
        return "TopUpForm[amount=" + getAmount() + "]";
    }
}
