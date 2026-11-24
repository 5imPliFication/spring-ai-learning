package com.example.ai;

import com.example.ai.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The test profile allows 5 auth attempts per minute per IP. */
@IntegrationTest
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mvc;

    private MockHttpServletRequestBuilder login(String forwardedFor) {
        return post("/api/v1/auth/login")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nobody\",\"password\":\"whatever\"}");
    }

    @Test
    void sixthLoginAttemptFromSameClientIsThrottled() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("203.0.113.7")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void limitIsPerClientIpFromForwardedHeader() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(login("198.51.100.1"));
        }
        // Same proxy (MockMvc remote address), different real client: not throttled (401 = wrong password, not 429).
        mvc.perform(login("198.51.100.2")).andExpect(status().isUnauthorized());
    }
}
