package com.rehand.exception;

/**
 * Thrown by services when a business rule is broken (for example "balance too low"
 * or "only the owner can edit a listing"). {@link GlobalExceptionHandler} turns it into
 * a flash message and redirects back to the page the user came from.
 */
public class BusinessRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
