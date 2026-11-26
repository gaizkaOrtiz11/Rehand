package com.rehand.dto;

import java.time.YearMonth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CardExpiryValidator implements ConstraintValidator<CardExpiry, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || PaymentChecks.isValidExpiry(value, YearMonth.now());
    }
}
