package com.example.urlshortener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms that disabling the master rate-limit switch removes all limiting
 * (Phase 9 task §11: "disabled rate limiting allows requests"). Runs under its own
 * Spring context (distinct property source) so it does not affect the main suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.rate-limit.enabled=false"})
class RateLimitDisabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private static int sequence = 0;

    @Test
    void manyRegistrationsFromSameIpAreAllowedWhenDisabled() throws Exception {
        // register-limit is 3 by default, but the master switch is off → none 429.
        for (int i = 0; i < 10; i++) {
            String body = "{ \"email\": \"off-" + (++sequence) + "@example.com\", \"password\": \"securePassword123\" }";
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }
}