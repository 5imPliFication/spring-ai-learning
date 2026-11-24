package com.example.ai.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registers throwaway users through the real API. */
public final class TestUsers {

    public record Registered(String userId, String username, String password, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    private TestUsers() {
    }

    /** A fresh client IP per call, so the per-IP auth rate limit never leaks between tests. */
    public static RequestPostProcessor uniqueIp() {
        int n = IP_COUNTER.getAndIncrement();
        String ip = "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    public static Registered register(MockMvc mvc, String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "_" + suffix;
        String password = "password123";
        String body = """
                {"username":"%s","password":"%s","displayName":"%s","email":"%s@example.com"}
                """.formatted(username, password, prefix, username);
        String json = mvc.perform(post("/api/v1/auth/register").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(JsonPath.read(json, "$.userId"), username, password, JsonPath.read(json, "$.token"));
    }
}
