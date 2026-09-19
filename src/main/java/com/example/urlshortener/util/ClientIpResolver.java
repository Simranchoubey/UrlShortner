package com.example.urlshortener.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the originating client IP from an {@link HttpServletRequest}.
 *
 * <p>Shares one convention across the codebase (Phase 8 click metadata and Phase 9
 * rate limiting): if a trusted proxy has supplied {@code X-Forwarded-For}, take the
 * first (original) address in the chain, otherwise fall back to
 * {@link HttpServletRequest#getRemoteAddr()}.
 *
 * <p>This is intentionally small — no networking abstraction. The app trusts
 * {@code X-Forwarded-For} the same way it already did for click analytics; operators
 * running behind a proxy should configure it so only trusted proxies can set the
 * header. A blank/missing header falls back to {@code getRemoteAddr()}.
 *
 * @param request the incoming HTTP request
 * @return the best-effort client IP (never {@code null}; empty only if the remote
 *         address itself is unavailable)
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}