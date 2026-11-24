package com.example.ai.services;

import java.util.regex.Pattern;

/**
 * Accumulates streamed model text and decides what is safe to show users while it's still arriving.
 * <p>
 * Two concerns:
 * <ul>
 *   <li>The model may embed {@code AZURA_ACTION_<uuid>} for calendar actions. That marker must never be shown,
 *       even partially, so complete markers are removed and a trailing partial one is held back.</li>
 *   <li>When the model writes a preamble, calls a tool, then answers, the two responses arrive back to back
 *       with no separator ("...date first.Today is..."). A space is inserted at such joins.</li>
 * </ul>
 * Not thread-safe; one instance per reply.
 */
final class StreamingText {

    static final String MARKER = "AZURA_ACTION_";
    private static final int ID_LENGTH = 36;
    private static final Pattern COMPLETE_MARKER = Pattern.compile(MARKER + "[0-9a-zA-Z-]{" + ID_LENGTH + "}");

    private final StringBuilder raw = new StringBuilder();

    void append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        if (needsSpaceBetween(raw, delta)) {
            raw.append(' ');
        }
        raw.append(delta);
    }

    /** Everything received, markers included; this is what gets post-processed and saved. */
    String raw() {
        return raw.toString();
    }

    /** What users may see right now. */
    String visible() {
        String text = COMPLETE_MARKER.matcher(raw).replaceAll("");
        return holdBackPartialMarker(text);
    }

    /** "first.Today" or "done!Next": sentence end directly followed by a new sentence. */
    private static boolean needsSpaceBetween(CharSequence before, String after) {
        if (before.isEmpty()) {
            return false;
        }
        char last = before.charAt(before.length() - 1);
        char next = after.charAt(0);
        return (last == '.' || last == '!' || last == '?') && Character.isUpperCase(next)
                // "U.S." style abbreviations: a single capital before the dot means we're mid-acronym
                && !(before.length() >= 2 && Character.isUpperCase(before.charAt(before.length() - 2)));
    }

    private static String holdBackPartialMarker(String text) {
        int markerAt = text.lastIndexOf(MARKER);
        if (markerAt >= 0) {
            // A full marker would have been removed already, so this one is still incomplete.
            return text.substring(0, markerAt);
        }
        for (int k = Math.min(MARKER.length() - 1, text.length()); k > 0; k--) {
            if (text.endsWith(MARKER.substring(0, k))) {
                return text.substring(0, text.length() - k);
            }
        }
        return text;
    }
}
