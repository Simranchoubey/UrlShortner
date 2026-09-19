package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshortener.domain.Url;
import com.example.urlshortener.domain.User;
import com.example.urlshortener.repository.UrlRepository;
import com.example.urlshortener.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer integration tests for Phase 6 authentication: registration, login,
 * JWT validation, protected/public endpoints, and the authenticated-owner link on
 * URL creation. Runs against H2 under the {@code test} profile (no Docker).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private static int sequence = 0;

    /** Serialises a map to a JSON body string via the shared ObjectMapper. */
    private String json(Map<String, Object> fields) throws Exception {
        return objectMapper.writeValueAsString(fields);
    }

    /** Registers a fresh user and logs in; returns the bearer token. */
    private String bearerToken() throws Exception {
        String email = "auth" + (++sequence) + "@example.com";
        register(email, "securePassword123");
        return "Bearer " + loginToken(email, "securePassword123");
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated());
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Builds a token whose expiry is already in the past. */
    private String expiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("999999")
                .issuedAt(past.minusSeconds(60))
                .expiresAt(past)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

// --- Registration --------------------------------------------------------------

    @Test
    void registrationReturns201WithIdAndEmailAndNeverThePassword() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "newuser@example.com", "password", "strongPass123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("id")).isTrue();
        assertThat(body.has("password")).isFalse(); // never expose the hash
    }

    @Test
    void passwordIsStoredHashed() throws Exception {
        String email = "hashcheck" + (++sequence) + "@example.com";
        register(email, "mySecretPw123");

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPassword()).isNotEqualTo("mySecretPw123");
        assertThat(passwordEncoder.matches("mySecretPw123", stored.getPassword())).isTrue();
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        register("dup@example.com", "securePassword123");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "dup@example.com", "password", "anotherPass456"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", "securePassword123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weakPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "weak@example.com", "password", "short"))))
                .andExpect(status().isBadRequest());
    }
// --- Login ----------------------------------------------------------------------

    @Test
    void validLoginReturns200WithBearerToken() throws Exception {
        String email = "loginok" + (++sequence) + "@example.com";
        register(email, "securePassword123");

        MvcResult auth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "securePassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(auth.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(token.split("\\.")).hasSize(3); // a JWT has 3 segments
    }

    @Test
    void wrongPasswordAndUnknownEmailBothReturnGeneric401() throws Exception {
        String email = "loginfail" + (++sequence) + "@example.com";
        register(email, "securePassword123");

        MvcResult wrongPw = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "wrongPassword"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        MvcResult unknownEmail = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.com", "password", "securePassword123"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Both failures share the same generic message → user enumeration is not
        // possible. (Timestamps differ per request, so compare the semantic fields.)
        String wrongPwMessage = objectMapper.readTree(wrongPw.getResponse().getContentAsString())
                .get("message").asText();
        String unknownEmailMessage = objectMapper.readTree(unknownEmail.getResponse().getContentAsString())
                .get("message").asText();
        assertThat(wrongPwMessage).isEqualTo(unknownEmailMessage).isEqualTo("Invalid email or password");
    }
// --- JWT / protected endpoints ----------------------------------------------------

    @Test
    void urlCreationWithoutTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalUrl", "https://example.com/x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void urlCreationWithInvalidTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalUrl", "https://example.com/x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void urlCreationWithExpiredTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalUrl", "https://example.com/x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void urlCreationWithValidTokenSucceedsAndStoresOwnerId() throws Exception {
        String email = "owner" + (++sequence) + "@example.com";
        register(email, "securePassword123");
        User owner = userRepository.findByEmail(email).orElseThrow();
        String token = loginToken(email, "securePassword123");

        MvcResult result = mockMvc.perform(post("/api/v1/urls")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("originalUrl", "https://example.com/owned"))))
                .andExpect(status().isCreated())
                .andReturn();

        String shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();
        Url persisted = urlRepository.findByShortCode(shortCode).orElseThrow();
        assertThat(persisted.getUser()).isNotNull();
        assertThat(persisted.getUser().getId()).isEqualTo(owner.getId());
    }

    @Test
    void tokenSubjectIsTheAuthenticatedUsersId() throws Exception {
        String email = "subj" + (++sequence) + "@example.com";
        register(email, "securePassword123");
        User user = userRepository.findByEmail(email).orElseThrow();
        String token = loginToken(email, "securePassword123");

        // Decode the payload's sub claim (base64url) and verify it equals the user id.
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"" + user.getId() + "\"");
    }
// --- Public endpoints ---------------------------------------------------------------

    @Test
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginRemainsPublic() throws Exception {
        // Login for a nonexistent user is still publicly reachable (returns 401, not 403).
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.com", "password", "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redirectRemainsPublic() throws Exception {
        // Seed a URL in the DB directly, then confirm the public redirect works with
        // no Authorization header.
        Url url = new Url();
        url.setShortCode("pubredir");
        url.setOriginalUrl("https://example.com/public");
        urlRepository.saveAndFlush(url);

        mockMvc.perform(get("/pubredir"))
                .andExpect(status().isFound());
    }
}