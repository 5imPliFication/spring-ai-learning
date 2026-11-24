package com.example.ai;

import com.example.ai.support.TestcontainersConfig;
import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs the app locally with no secrets: Postgres from Testcontainers and a fake model that streams a canned
 * reply slowly, so the streaming UI can be watched in a browser.
 * <pre>./mvnw spring-boot:test-run</pre>  then  <pre>cd frontend && npm run dev</pre>
 */
public class TestAiApplication {

    public static void main(String[] args) {
        SpringApplication.from(AiApplication::main)
                .with(TestcontainersConfig.class, FakeModelConfig.class)
                .run("--spring.profiles.active=test", "--server.port=8080");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeModelConfig {

        static final String REPLY = "Sure! How about Friday at 5pm? That works for everyone who has replied so far, "
                + "and it leaves plenty of time to book a table somewhere nice.";

        @Bean
        @Primary
        DeepSeekChatModel fakeStreamingModel() {
            DeepSeekChatModel model = Mockito.mock(DeepSeekChatModel.class);
            when(model.getOptions()).thenReturn(DeepSeekChatOptions.builder().build());
            when(model.getDefaultOptions()).thenReturn(DeepSeekChatOptions.builder().build());
            when(model.call(any(Prompt.class))).thenReturn(response(REPLY, true));
            when(model.stream(any(Prompt.class))).thenAnswer(inv -> {
                Prompt prompt = inv.getArgument(0);
                List<String> words = Arrays.stream(REPLY.split("(?<= )")).toList();
                Flux<ChatResponse> stream = Flux.range(0, words.size())
                        .map(i -> response(words.get(i), i == words.size() - 1))
                        .delayElements(Duration.ofMillis(120));
                // The transcript ends with the newest message, so "@ai fail" there simulates a mid-stream failure.
                String newestLine = prompt.getUserMessage().getText().lines()
                        .filter(line -> line.contains(": "))
                        .reduce((a, b) -> b)
                        .orElse("");
                boolean fail = newestLine.contains("@ai fail");
                return fail
                        ? stream.take(6).concatWith(Flux.error(new IllegalStateException("simulated model failure")))
                        : stream;
            });
            return model;
        }

        private static ChatResponse response(String text, boolean last) {
            var generations = List.of(new Generation(new AssistantMessage(text)));
            return last
                    ? new ChatResponse(generations, ChatResponseMetadata.builder().usage(new DefaultUsage(100, 30, 130)).build())
                    : new ChatResponse(generations);
        }
    }
}
