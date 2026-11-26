package com.rehand.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** 13 to 19 digits (spaces allowed) passing the Luhn check. Blank values are left to {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = CardNumberValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CardNumber {

    String message() default "Enter a valid card number (13 to 19 digits).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
