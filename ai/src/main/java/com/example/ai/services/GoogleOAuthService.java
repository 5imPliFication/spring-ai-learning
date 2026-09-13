package com.example.ai.services;

import com.example.ai.dto.OAuthStatusResponse;
import com.example.ai.entity.UserOAuthAccount;
import com.example.ai.repository.UserOAuthAccountRepository;
import com.example.ai.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GoogleOAuthService {

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final UserOAuthAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    @Value("${app.oauth.google.client-id:}")
    private String clientId;

    @Value("${app.oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${app.oauth.google.redirect-uri:}")
    private String redirectUri;

    @Value("${app.oauth.google.scopes:}")
    private String scopes;

    public GoogleOAuthService(UserOAuthAccountRepository accountRepository,
                              UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    private record PendingState(String userId, Instant expiresAt) {}

    public String buildAuthorizationUrl(String userId) {
        requireConfigured();
        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(userId, Instant.now().plus(STATE_TTL)));

        return AUTH_ENDPOINT
                + "?client_id=" + urlEncode(clientId)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + urlEncode(scopes)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&include_granted_scopes=true"
                + "&state=" + urlEncode(state);
    }

    public OAuthStatusResponse handleCallback(String code, String state) {
        PendingState pending = pendingStates.remove(state);
        if (pending == null) {
            throw new IllegalArgumentException("Invalid or expired OAuth state. Please start over.");
        }
        if (pending.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("OAuth state expired. Please try again.");
        }

        Map<String, Object> token = exchangeForToken(Map.of(
                "code", code,
                "grant_type", "authorization_code"
        ));

        String accessToken = stringValue(token, "access_token");
        String refreshToken = stringValue(token, "refresh_token");
        String tokenType = stringValue(token, "token_type");
        String scope = stringValue(token, "scope");
        Long expiresIn = longValue(token, "expires_in");

        String googleEmail = fetchGoogleEmail(accessToken);
        if (googleEmail == null || googleEmail.isBlank()) {
            throw new IllegalStateException("Could not determine the Google account email.");
        }

        userRepository.findById(pending.userId()).ifPresent(user -> {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail(googleEmail);
                userRepository.save(user);
            }
        });

        UserOAuthAccount account = accountRepository.findByUserId(pending.userId())
                .orElseGet(() -> UserOAuthAccount.builder().userId(pending.userId()).build());
        account.setGoogleEmail(googleEmail);
        account.setAccessToken(accessToken);
        if (refreshToken != null) {
            account.setRefreshToken(refreshToken);
        }
        account.setTokenType(tokenType != null ? tokenType : "Bearer");
        account.setScope(scope);
        account.setExpiresAt(expiresIn != null ? Instant.now().plusSeconds(expiresIn) : null);
        accountRepository.save(account);

        log.info("Google account connected for user {}", pending.userId());
        return new OAuthStatusResponse(true, googleEmail);
    }

    public OAuthStatusResponse status(String userId) {
        return accountRepository.findByUserId(userId)
                .map(a -> new OAuthStatusResponse(true, a.getGoogleEmail()))
                .orElseGet(() -> new OAuthStatusResponse(false, null));
    }

    public void disconnect(String userId) {
        accountRepository.findByUserId(userId).ifPresent(account -> {
            revokeToken(account.getAccessToken());
            accountRepository.delete(account);
            log.info("Google account disconnected for user {}", userId);
        });
    }

    public Optional<String> validAccessToken(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return accountRepository.findByUserId(userId).map(this::ensureFreshToken);
    }

    private String ensureFreshToken(UserOAuthAccount account) {
        Instant now = Instant.now();
        if (account.getExpiresAt() != null && account.getExpiresAt().isAfter(now.plusSeconds(60))) {
            return account.getAccessToken();
        }
        if (account.getRefreshToken() == null || account.getRefreshToken().isBlank()) {
            throw new IllegalStateException("Calendar access has expired and there is no refresh token. Reconnect Google Calendar.");
        }

        Map<String, Object> token = exchangeForToken(Map.of(
                "refresh_token", account.getRefreshToken(),
                "grant_type", "refresh_token"
        ));

        String newAccessToken = stringValue(token, "access_token");
        if (newAccessToken == null) {
            throw new IllegalStateException("Failed to refresh Google access token.");
        }
        Long expiresIn = longValue(token, "expires_in");
        account.setAccessToken(newAccessToken);
        account.setExpiresAt(expiresIn != null ? Instant.now().plusSeconds(expiresIn) : null);
        accountRepository.save(account);
        return newAccessToken;
    }

    private Map<String, Object> exchangeForToken(Map<String, String> extraForm) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirectUri);
        form.putAll(extraForm);

        HttpResponse<String> response = postForm(TOKEN_ENDPOINT, form);
        return parseJson(response.body());
    }

    private String fetchGoogleEmail(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USERINFO_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to fetch Google profile: HTTP " + response.statusCode() + " " + response.body());
        }
        return stringValue(parseJson(response.body()), "email");
    }

    private void revokeToken(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REVOKE_ENDPOINT + "?token=" + urlEncode(accessToken)))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            send(request);
        } catch (Exception e) {
            log.warn("Failed to revoke Google token: {}", e.getMessage());
        }
    }

    private HttpResponse<String> postForm(String endpoint, Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) body.append("&");
            body.append(urlEncode(k)).append("=").append(urlEncode(v));
        });

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google OAuth request failed: HTTP " + response.statusCode() + " " + response.body());
        }
        return response;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Network error talking to Google: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while talking to Google.", e);
        }
    }

    private Map<String, Object> parseJson(String body) {
        try {
            return objectMapper.readValue(body, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected response from Google: " + body, e);
        }
    }

    @Scheduled(fixedDelay = 60000)
    void evictExpiredStates() {
        Instant now = Instant.now();
        pendingStates.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()
                || redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException("Google OAuth is not configured on the server.");
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static Long longValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }
}