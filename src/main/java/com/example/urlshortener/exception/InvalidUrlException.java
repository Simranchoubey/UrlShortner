package com.example.urlshortener.exception;

/**
 * Thrown when a submitted URL, alias, or expiration time fails business-rule
 * validation. Mapped by {@link GlobalExceptionHandler} to {@code 400 Bad Request}.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}