package com.example.urlshortener.exception;

/**
 * Thrown when a requested short code does not match any stored URL.
 * Mapped by {@link GlobalExceptionHandler} to {@code 404 Not Found}.
 */
public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String message) {
        super(message);
    }
}