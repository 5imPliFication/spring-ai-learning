package com.example.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Browser origins allowed to call the API and open the WebSocket.
 * Configured via {@code app.cors.allowed-origins} (comma-separated, defaults to FRONTEND_BASE_URL).
 * Entries may be patterns such as {@code https://*.up.railway.app}.
 */
@Component
public class AllowedOrigins {

    private final List<String> patterns;

    public AllowedOrigins(@Value("${app.cors.allowed-origins}") String raw) {
        this.patterns = Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .toList();
        if (patterns.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins must list at least one origin");
        }
    }

    public List<String> patterns() {
        return patterns;
    }

    public String[] patternsArray() {
        return patterns.toArray(String[]::new);
    }
}
