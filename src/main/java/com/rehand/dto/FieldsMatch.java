package com.rehand.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level constraint: the properties {@code first} and {@code second} must hold the same text.
 * The violation is reported on the {@code second} property, so the form shows the message under
 * the "confirm" field. A missing value is ignored here (the {@code @NotBlank} on the field reports it).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = FieldsMatchValidator.class)
public @interface FieldsMatch {

    String message() default "The two values do not match.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Name of the first property, for example {@code password}. */
    String first();

    /** Name of the second property, for example {@code confirmPassword}. */
    String second();
}
