package com.example.ai.services;

import com.example.ai.entity.RoomMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AzuraServiceTranscriptTest {

    private static RoomMessage msg(String sender, String type, String content) {
        return RoomMessage.builder().senderId(sender).messageType(type).content(content).build();
    }

    @Test
    void buildsNameColonTextLinesAndSummarisesMedia() {
        String transcript = AzuraService.buildTranscript(List.of(
                msg("u1", "TEXT", "anyone free friday?"),
                msg("u2", "IMAGE", "https://cdn/x.png"),
                msg("ai-bot", null, "I am!"),
                msg("ghost", "FILE", "x")
        ), Map.of("u1", "Alice", "u2", "Bob", "ai-bot", "Azura"));

        assertThat(transcript).isEqualTo("""
                Alice: anyone free friday?
                Bob: [Image]
                Azura: I am!
                Unknown: [File]
                """);
    }

    @Test
    void processActionTokenStripsMarker() {
        var service = new AzuraService(null, null, null, null, new PendingCalendarActionService());
        var processed = service.processActionToken(
                "Done! AZURA_ACTION_123e4567-e89b-12d3-a456-426614174000", "u1");
        assertThat(processed.content()).isEqualTo("Done!");
    }
}
