package com.example.urlshortener.util;

import com.example.urlshortener.exception.InvalidUrlException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Business-rule validation for submitted URLs and custom aliases (§3, §7 of the
 * plan: "Util → validators"). {@link URI} parsing rejects obviously malformed
 * URLs, then we enforce the scheme allow-list and control-character rules.
 */
public final class UrlValidator {

    private static final int MAX_URL_LENGTH = 2048;
    // Aliases that are caller-supplied short codes must fit the short_code column,
    // which is VARCHAR(12) (mirrors the entity's length=12). Keeping the advertised
    // max at 12 prevents a value-too-long insert failure at the DB.
    private static final int MAX_ALIAS_LENGTH = 12;
    private static final String ALIAS_PATTERN = "[A-Za-z0-9_-]+";

    private UrlValidator() {
    }

    /**
     * Validates that {@code originalUrl} is non-blank, not too long, contains no
     * control characters, and is an absolute {@code http} or {@code https} URL.
     *
     * @throws InvalidUrlException on any violation
     */
    public static void validateOriginalUrl(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new InvalidUrlException("originalUrl is required");
        }
        if (originalUrl.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("originalUrl must not exceed " + MAX_URL_LENGTH + " characters");
        }
        rejectControlCharacters(originalUrl, "originalUrl");

        URI uri;
        try {
            uri = new URI(originalUrl.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("originalUrl is not a valid URL");
        }

        if (uri.getScheme() == null) {
            throw new InvalidUrlException("originalUrl must be an absolute URL with a scheme");
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!("http".equals(scheme) || "https".equals(scheme))) {
            throw new InvalidUrlException("originalUrl scheme must be http or https");
        }
        if (uri.getHost() == null && uri.getRawAuthority() == null) {
            throw new InvalidUrlException("originalUrl must include a host");
        }
    }

    /**
     * Validates an optional custom alias: length bounds and URL-safe character
     * set. Returns false when the alias is absent (nothing to validate).
     *
     * @throws InvalidUrlException if the alias is present but invalid
     */
    public static boolean validateCustomAlias(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return false;
        }
        if (customAlias.length() < 3 || customAlias.length() > MAX_ALIAS_LENGTH) {
            throw new InvalidUrlException(
                    "customAlias must be between 3 and " + MAX_ALIAS_LENGTH + " characters");
        }
        if (!customAlias.matches(ALIAS_PATTERN)) {
            throw new InvalidUrlException("customAlias may only contain letters, digits, '-' and '_'");
        }
        return true;
    }

    private static void rejectControlCharacters(String value, String field) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new InvalidUrlException(field + " must not contain control characters");
            }
        }
    }
}