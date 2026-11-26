package com.rehand.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** IBAN format and mod-97 checksum (spaces allowed). Blank values are left to {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = IbanValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Iban {

    String message() default "Enter a valid IBAN, for example LT12 1000 0111 0100 1000.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
