package com.example.urlshortener.dto;

/**
 * Response body for a successful registration ({@code 201 Created}).
 *
 * <p>Contains only the user's id and email — the password hash is never exposed.
 *
 * @param id    the newly-created user's id
 * @param email the registered email
 */
public record RegisterResponse(
        Long id,
        String email) {
}