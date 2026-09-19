package com.example.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the real {@link ShortCodeGenerator}: each generated code must
 * be the configured length, URL-safe Base62-only, and effectively non-repeating.
 */
class ShortCodeGeneratorTest {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesCodesOfExpectedLength() {
        assertThat(generator.generate()).hasSize(ShortCodeGenerator.DEFAULT_LENGTH); // 7 per §7
        assertThat(generator.generate()).hasSize(ShortCodeGenerator.DEFAULT_LENGTH);
    }

    @Test
    void codesContainOnlyBase62Characters() {
        for (int i = 0; i < 50; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(7);
            for (char c : code.toCharArray()) {
                assertThat(BASE62).contains(String.valueOf(c));
            }
        }
    }

    @Test
    void codesVaryBetweenCalls() {
        // Overwhelmingly unlikely to be all equal by chance for these many draws;
        // guards against a degenerate constant generator.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen.size()).isGreaterThan(1);
    }

    @Test
    void generateWithExplicitLengthHonoursIt() {
        assertThat(generator.generate(5)).hasSize(5);
    }
}