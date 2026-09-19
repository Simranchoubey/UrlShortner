package com.example.urlshortener.security;

import com.example.urlshortener.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues and validates short-lived, stateless JWT access tokens (§7–§8 of the
 * plan).
 *
 * <p>The token's {@code sub} (subject) is the user id and it always carries an
 * {@code exp} expiry (15 minutes by default). No sensitive data is put in the
 * token. Signing uses HMAC-SHA (HS256) with a secret configured via the
 * {@code JWT_SECRET} environment variable — never hardcoded here.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long ttlMinutes;

    public JwtService(JwtEncoder encoder, JwtDecoder decoder,
                      @org.springframework.beans.factory.annotation.Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Creates a signed access token for the given user.
     *
     * @param user the authenticated user (id + email used as claims)
     * @return the compact JWT string
     */
    public String issueToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttlMinutes, ChronoUnit.MINUTES);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        // Explicitly pin the HS256 header so the encoder always selects the HMAC
        // key regardless of how the JWK source is configured.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Validates the token and returns the authenticated user's id (the JWT
     * subject). Throws on a missing/expired/invalid token.
     */
    public Long parseSubject(String token) {
        Jwt jwt = decoder.decode(token);
        return Long.valueOf(jwt.getSubject());
    }
}