package com.example.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1")
public class AIController {

    private final ChatClient chatClient;
    @Value("classpath:/prompts/Deepseek.st")
    private Resource deepseekPrompt;

    @Autowired
    private ChatModel chatModel;

    public AIController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping(path = "/ai", produces = "text/html;charset=utf-8")
    public Flux<String> generateIntroduction(@RequestParam(value = "message", defaultValue = "Hello, who are you?") String message) {
        if (message == null || message.isEmpty()) {
            return Flux.empty();
        }

/*        SystemMessage sys = new SystemMessage("Your primary function is to make the user understand more about you. Introduce yourself when greeted and showcase some functionalities");
            UserMessage user = new UserMessage("Hello, introduce yourself");
            Prompt prompt = new Prompt(List.of(sys, user));
 */

        PromptTemplate promptTemplate = new PromptTemplate(deepseekPrompt);

        Prompt prompt = promptTemplate.create(Map.of("message", message));

        return chatClient.prompt(prompt)
                .user(message) // Your input prompt
                .stream()
                .content(); // The AI's response
    }

    @GetMapping(path = "/list")
    public List<String> generateList(@RequestParam(value = "message", defaultValue = "the powerful AI models") String aiModels) {
        if (aiModels == null || aiModels.trim().isEmpty()) {
            return Collections.emptyList();
        }

        var converter = new ListOutputConverter(new DefaultConversionService());

        return ChatClient.create(chatModel).prompt()
                .user(u -> u.text(
                        """
                                List out top 10 of {aiModels} from top to bottom. If you do not know the answer or not sure, just response with "I don't know" or
                                "I'm not sure". {format}
                                """).param("aiModels", aiModels).param("format", converter.getFormat()))
                .call()
                .entity(converter);
    }

    @GetMapping(path = "map")
    public Map<String, String> generateMap(@RequestParam(value = "message", defaultValue = "the best selling books of all time") String bestBooks) {
        if (bestBooks == null || bestBooks.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        var rawResponse = ChatClient.create(chatModel).prompt()
                .options(DeepSeekChatOptions.builder().temperature(2.0))
                .user(u -> u.text(
                        """
                                Give me a key-value pairs for {bestBooks}, including the name of the book as the key and its number of copy sold as the value. If you do not know the answer or not sure, just response with "I don't know" or
                                "I'm not sure".
                                """).param("bestBooks", bestBooks))
                .call()
                .entity(new ParameterizedTypeReference<Map<String, String>>() {
                });
        System.out.println("RAW: " + rawResponse);
        return rawResponse;
    }
}
