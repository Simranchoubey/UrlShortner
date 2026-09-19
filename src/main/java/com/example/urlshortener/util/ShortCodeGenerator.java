package com.example.urlshortener.util;

import java.security.SecureRandom;

/**
 * Random Base62 short-code generator.
 *
 * <p>The alphabet is {@code a-z A-Z 0-9}, which is URL-safe and compact. With
 * 7 characters there are {@code 62^7 ≈ 3.5 trillion} possible codes. Codes are
 * generated with a cryptographically strong {@link SecureRandom} so they are
 * unpredictable (users cannot guess other people's short links), per §7 of the
 * implementation plan (Random Base62, not sequential IDs).
 *
 * <p>Uniqueness is enforced by the database unique constraint on
 * {@code urls.short_code}; this class only ever produces well-formed candidates.
 */
public final class ShortCodeGenerator {

    /** Default code length per §7 (7 chars → ~3.5 trillion combinations). */
    public static final int DEFAULT_LENGTH = 7;

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int ALPHABET_SIZE = ALPHABET.length();

    private final SecureRandom random;

    public ShortCodeGenerator() {
        this.random = new SecureRandom();
    }

    /**
     * Generates a random Base62 string of {@code length} characters.
     *
     * @throws IllegalArgumentException if {@code length} is not positive
     */
    public String generate(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET_SIZE)));
        }
        return sb.toString();
    }

    /** Generates a random Base62 string of the default length (7). */
    public String generate() {
        return generate(DEFAULT_LENGTH);
    }
}