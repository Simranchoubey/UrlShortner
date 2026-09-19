package com.example.urlshortener.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>Validation mirrors registration so clients get consistent {@code 400}s for
 * malformed input. A login failure (unknown email or wrong password) is handled
 * in the auth service as a generic {@code 401} that never reveals which part was
 * wrong — see §6 of the plan.
 *
 * @param email    the account email
 * @param password the account password
 */
public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must not exceed 255 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must not exceed 72 characters")
        String password) {
}