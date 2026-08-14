package com.example.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatConfig {
    @Bean
    @Primary
    ChatClient primaryChatClient(DeepSeekChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
