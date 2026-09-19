package com.example.urlshortener.dto;

/**
 * Response body for a successful login ({@code 200 OK}).
 *
 * <p>The client sends this token back in the {@code Authorization: Bearer <token>}
 * header on protected endpoints.
 *
 * @param accessToken the signed JWT access token
 * @param tokenType   the token type, always {@code "Bearer"}
 */
public record LoginResponse(
        String accessToken,
        String tokenType) {

    public static LoginResponse bearer(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}