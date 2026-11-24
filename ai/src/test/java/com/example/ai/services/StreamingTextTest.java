package com.example.ai.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingTextTest {

    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";

    private static StreamingText of(String... chunks) {
        var t = new StreamingText();
        for (String c : chunks) t.append(c);
        return t;
    }

    @Test
    void joinsChunksVerbatim() {
        assertThat(of("Hel", "lo ", "world").visible()).isEqualTo("Hello world");
    }

    @Test
    void insertsSpaceWhereAToolCallSplitTwoSentences() {
        // Seen live: preamble, tool call, then the answer with no separator.
        assertThat(of("I'll look up today's date first.", "Today is Tuesday.").raw())
                .isEqualTo("I'll look up today's date first. Today is Tuesday.");
    }

    @Test
    void doesNotSplitAcronymsOrDecimals() {
        assertThat(of("Made in the U.", "S. today").raw()).isEqualTo("Made in the U.S. today");
        assertThat(of("Version 2.", "5 is out").raw()).isEqualTo("Version 2.5 is out");
    }

    @Test
    void removesCompleteMarker() {
        assertThat(of("Approve this: AZURA_ACTION_" + ID + " thanks").visible())
                .isEqualTo("Approve this:  thanks");
    }

    @Test
    void holdsBackPartialMarkerUntilComplete() {
        assertThat(of("Approve this: AZU").visible()).isEqualTo("Approve this: ");
        assertThat(of("Approve this: AZURA_ACTION_123e").visible()).isEqualTo("Approve this: ");
        assertThat(of("Approve this: AZURA_ACTION_" + ID).visible()).isEqualTo("Approve this: ");
    }

    @Test
    void rawKeepsMarkerForPostProcessing() {
        assertThat(of("x AZURA_ACTION_", ID).raw()).contains("AZURA_ACTION_" + ID);
    }

    @Test
    void ignoresNullAndEmptyChunks() {
        assertThat(of(null, "", "ok").raw()).isEqualTo("ok");
    }
}
