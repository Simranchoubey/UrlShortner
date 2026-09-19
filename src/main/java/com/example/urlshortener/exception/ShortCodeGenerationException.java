package com.example.urlshortener.exception;

/**
 * Thrown when a unique short code could not be generated after exhausting the
 * bounded retry budget. Mapped by {@link GlobalExceptionHandler} to
 * {@code 500 Internal Server Error} because it should be extraordinarily rare.
 */
public class ShortCodeGenerationException extends RuntimeException {

    public ShortCodeGenerationException(String message) {
        super(message);
    }
}