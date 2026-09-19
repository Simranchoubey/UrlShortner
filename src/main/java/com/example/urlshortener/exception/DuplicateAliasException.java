package com.example.urlshortener.exception;

/**
 * Thrown when a submitted custom alias is unavailable because it already exists.
 * Mapped by {@link GlobalExceptionHandler} to {@code 409 Conflict}.
 */
public class DuplicateAliasException extends RuntimeException {

    public DuplicateAliasException(String message) {
        super(message);
    }
}