package com.example.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("api/v1/ai")
public class AIController {

    private final ChatClient chatClient;
    public AIController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping(produces = "text/html;charset=utf-8")
    public Flux<String> completion(@RequestParam String message) {
        if (message == null || message.isEmpty()) {
            return Flux.empty();
        }
        return chatClient.prompt()
                .user(message) // Your input prompt
                .stream()
                .content(); // The AI's response
    }
}
