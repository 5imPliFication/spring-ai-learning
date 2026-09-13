package com.example.ai.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DeepSeekToolCallingSpikeTest {

    private static String resolveApiKey() throws IOException {
        String envKey = System.getenv("AI-API-KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        for (Path candidate : List.of(Path.of(".env"), Path.of("../.env"))) {
            if (!Files.exists(candidate)) continue;
            for (String line : Files.readAllLines(candidate)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("AI-API-KEY")) continue;
                String value = trimmed.replaceFirst("^AI-API-KEY\\s*=\\s*", "");
                if (!value.isBlank()) return value;
            }
        }
        return null;
    }

    @Test
    void deepSeekCallsTheDateToolInsteadOfGuessing() throws IOException {
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null, "AI-API-KEY not found in env or ai/.env; skipping live spike");

        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl("https://api.deepseek.com/v1")
                        .apiKey(apiKey)
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                        .maxTokens(256)
                        .build())
                .build();

        ChatClient client = ChatClient.builder(model)
                .defaultTools(new DateTimeTools())
                .build();

        String response = client.prompt()
                .user("Use the getCurrentDate tool to find today's date, then reply with just that date.")
                .call()
                .content();

        System.out.println("====== LLM RESPONSE ======");
        System.out.println(response);
        System.out.println("====== EXPECTED TODAY =====");
        System.out.println(LocalDate.now() + " (" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) + ")");

        assertThat(response)
                .containsIgnoringCase(LocalDate.now().toString())
                .containsIgnoringCase(String.valueOf(LocalDate.now().getYear()));
    }
}