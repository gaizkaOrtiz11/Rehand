package com.rehand.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Expiry date as MM/YY, not in the past. Blank values are left to {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = CardExpiryValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CardExpiry {

    String message() default "Enter a valid expiry date (MM/YY) that is not in the past.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
