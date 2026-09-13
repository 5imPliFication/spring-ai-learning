package com.example.ai.config;

import com.example.ai.tools.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;

@Configuration
public class ChatConfig {
    @Bean
    @Primary
    ChatClient primaryChatClient(DeepSeekChatModel chatModel,
                                  DateTimeTools dateTimeTools,
                                  ObjectProvider<ToolCallbackProvider> mcpProviders) {
        var builder = ChatClient.builder(chatModel)
                .defaultTools(dateTimeTools);

        mcpProviders.ifAvailable(provider -> {
            ToolCallback[] mcp = provider.getToolCallbacks();
            if (mcp != null && mcp.length > 0) {
                builder.defaultTools(mcp);
            }
        });

        return builder.build();
    }
}