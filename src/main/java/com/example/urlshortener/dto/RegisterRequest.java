package com.example.urlshortener.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * <p>Email and password presence/format are validated here (returned as
 * {@code 400}); registration logic in the auth service then hashes the password
 * and persists the account. The email is normalised (lower-cased) before storage.
 *
 * @param email    the account email (valid format, ≤ 255 chars)
 * @param password the chosen password (length bounded so it fits BCrypt's 72-byte cap)
 */
public record RegisterRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password) {
}