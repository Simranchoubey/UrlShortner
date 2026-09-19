package com.example.urlshortener.exception;

/**
 * Raised when a registration attempts to use an email that already exists.
 * Mapped to {@code 409 Conflict} by {@link GlobalExceptionHandler}.
 *
 * <p>The database unique constraint on {@code users.email} remains the final
 * protection against concurrent duplicate registrations, so this exception is a
 * convenience for the common (non-concurrent) case.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}