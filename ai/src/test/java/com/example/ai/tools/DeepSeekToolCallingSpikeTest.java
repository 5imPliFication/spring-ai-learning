package com.example.ai.tools;

import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarActionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static DeepSeekChatModel model(String apiKey) {
        return DeepSeekChatModel.builder()
                .deepSeekApi(DeepSeekApi.builder()
                        .baseUrl("https://api.deepseek.com/v1")
                        .apiKey(apiKey)
                        .build())
                .options(DeepSeekChatOptions.builder()
                        .model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH)
                        .maxTokens(256)
                        .build())
                .build();
    }

    @Test
    void deepSeekCallsTheDateToolInsteadOfGuessing() throws IOException {
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null, "AI-API-KEY not found in env or ai/.env; skipping live spike");

        DeepSeekChatModel model = model(apiKey);

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

    /**
     * Phase 3 gate: streaming must still run tools, and we want to know whether token usage arrives.
     * Prints the chunk count and the usage seen so the result can be read from the test output.
     */
    @Test
    void streamingStillCallsToolsAndEmitsMultipleChunks() throws IOException {
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null, "AI-API-KEY not found in env or ai/.env; skipping live spike");

        ChatClient client = ChatClient.builder(model(apiKey))
                .defaultTools(new DateTimeTools())
                .build();

        List<ChatResponse> chunks = client.prompt()
                .user("Use the getCurrentDate tool to find today's date. Then write two sentences about that date.")
                .stream()
                .chatResponse()
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(chunks).isNotNull();
        String text = chunks.stream()
                .filter(c -> c.getResult() != null && c.getResult().getOutput().getText() != null)
                .map(c -> c.getResult().getOutput().getText())
                .collect(Collectors.joining());
        Usage lastUsage = chunks.stream()
                .map(c -> c.getMetadata().getUsage())
                .filter(u -> u != null && u.getTotalTokens() != null && u.getTotalTokens() > 0)
                .reduce((a, b) -> b)
                .orElse(null);

        System.out.println("====== STREAM SPIKE ======");
        System.out.println("chunks=" + chunks.size());
        System.out.println("usage=" + (lastUsage == null ? "none" : lastUsage.getPromptTokens() + "/"
                + lastUsage.getCompletionTokens() + "/" + lastUsage.getTotalTokens()));
        System.out.println("text=" + text);

        assertThat(chunks.size()).as("response should arrive in several chunks").isGreaterThan(1);
        assertThat(text).contains(String.valueOf(LocalDate.now().getYear()));
    }

    /**
     * Phase 3 gate, part 2: the calendar tools, attached exactly as AzuraService attaches them
     * (ResilientToolCallback wrappers + userId/roomId tool context), must still be called while streaming.
     */
    @Test
    void streamingCallsCalendarToolsWithToolContext() throws IOException {
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null, "AI-API-KEY not found in env or ai/.env; skipping live spike");

        GoogleOAuthService oauth = Mockito.mock(GoogleOAuthService.class);
        Mockito.when(oauth.validAccessToken("spike-user")).thenReturn(Optional.empty());
        CalendarTools calendarTools = new CalendarTools(oauth, new PendingCalendarActionService());
        ToolCallback[] callbacks = Arrays.stream(ToolCallbacks.from(calendarTools))
                .map(ResilientToolCallback::new)
                .toArray(ToolCallback[]::new);

        List<ChatResponse> chunks = ChatClient.builder(model(apiKey))
                .defaultTools(new DateTimeTools())
                .build()
                .prompt()
                .user("Use the list_my_calendars tool to list my Google calendars, then tell me what it said.")
                .tools(callbacks)
                .toolContext(Map.of("userId", "spike-user", "roomId", "spike-room"))
                .stream()
                .chatResponse()
                .collectList()
                .block(Duration.ofSeconds(60));

        String text = chunks.stream()
                .filter(c -> c.getResult() != null && c.getResult().getOutput().getText() != null)
                .map(c -> c.getResult().getOutput().getText())
                .collect(Collectors.joining());
        System.out.println("====== CALENDAR STREAM SPIKE ======");
        System.out.println("chunks=" + chunks.size());
        System.out.println("text=" + text);

        // The tool ran, with the userId from the tool context, in the middle of a streamed response.
        Mockito.verify(oauth, Mockito.atLeastOnce()).validAccessToken("spike-user");
        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(text.toLowerCase()).containsAnyOf("connect", "google");
    }
}
