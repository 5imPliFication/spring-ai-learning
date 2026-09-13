package com.example.ai.controller;

import com.example.ai.dto.OAuthStatusResponse;
import com.example.ai.entity.User;
import com.example.ai.services.GoogleOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oauth/google")
@RequiredArgsConstructor
public class OAuthController {

    private final GoogleOAuthService googleOAuthService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(@AuthenticationPrincipal User user) {
        String authUrl = googleOAuthService.buildAuthorizationUrl(user.getId());
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(name = "code", required = false) String code,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "error", required = false) String error,
                         HttpServletResponse response) throws IOException {
        String redirect;
        try {
            if (code == null || state == null || code.isBlank() || state.isBlank()) {
                throw new IllegalArgumentException("Missing OAuth code or state.");
            }
            if (error != null && !error.isBlank()) {
                redirect = frontendBaseUrl + "?oauth=error&message=" + urlEncode("Google authorization was declined.");
            } else {
                googleOAuthService.handleCallback(code, state);
                redirect = frontendBaseUrl + "?oauth=success";
            }
        } catch (Exception e) {
            redirect = frontendBaseUrl + "?oauth=error&message=" + urlEncode(e.getMessage());
        }
        response.sendRedirect(redirect);
    }

    @GetMapping("/status")
    public ResponseEntity<OAuthStatusResponse> status(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(googleOAuthService.status(user.getId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        googleOAuthService.disconnect(user.getId());
        return ResponseEntity.noContent().build();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}