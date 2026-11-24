package com.example.ai;

import com.example.ai.support.IntegrationTest;
import com.example.ai.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.ai.support.TestUsers.uniqueIp;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registeredUserCanCallProtectedEndpointWithToken() throws Exception {
        var user = TestUsers.register(mvc, "auth");

        mvc.perform(get("/api/v1/users/me").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.username()));
    }

    @Test
    void loginReturnsTokenForCorrectPassword() throws Exception {
        var user = TestUsers.register(mvc, "login");

        mvc.perform(post("/api/v1/auth/login").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user.username(), user.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.userId()));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        var user = TestUsers.register(mvc, "badpw");

        mvc.perform(post("/api/v1/auth/login").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}".formatted(user.username())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginWithUnknownUsernameGetsTheSame401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no_such_user_xyz\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        var user = TestUsers.register(mvc, "dupe");

        mvc.perform(post("/api/v1/auth/register").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password123","displayName":"x","email":"other_%s@example.com"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
