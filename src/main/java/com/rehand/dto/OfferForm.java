package com.rehand.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * The amount of a new offer or a counter-offer. The upper bound (below the asking price) depends on the
 * listing, so {@link com.rehand.service.OfferService} checks it.
 */
public class OfferForm {

    @NotNull(message = "Enter the amount you want to offer.")
    @DecimalMin(value = "0.01", message = "Enter an amount above zero.")
    @Digits(integer = 8, fraction = 2, message = "Use euros with at most two decimals, for example 25.50.")
    private BigDecimal amount;

    public OfferForm() {
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
