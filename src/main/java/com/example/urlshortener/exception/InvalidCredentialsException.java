package com.example.urlshortener.exception;

/**
 * Raised when login credentials are invalid (unknown email OR wrong password).
 * Mapped to {@code 401 Unauthorized} by {@link GlobalExceptionHandler}.
 *
 * <p>The message is deliberately generic so clients cannot tell whether the email
 * or the password was wrong, avoiding user enumeration (§6 of the plan).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}