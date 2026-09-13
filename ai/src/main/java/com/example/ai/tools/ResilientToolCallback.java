package com.example.ai.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.Map;

/**
 * Wraps a {@link ToolCallback} to tolerate the slightly malformed JSON that LLMs
 * occasionally produce for tool arguments (trailing commas, single-quoted strings,
 * unquoted keys, double-encoded strings, NaN/Infinity). When the wrapped callback
 * fails to parse the input, we repair it with a lenient parser and re-invoke it, and
 * if that still fails we return a clear instruction to the model instead of letting
 * Spring AI log "Conversion from JSON failed" and throw.
 */
public class ResilientToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(ResilientToolCallback.class);

    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_TRAILING_COMMA)
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final int MAX_INPUT_IN_LOG = 500;

    private static final Class<?> JACKSON_EXCEPTION_TYPE = nullableClass("tools.jackson.core.JacksonException");

    private final ToolCallback delegate;

    public ResilientToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        if (toolInput == null || toolInput.isBlank()) {
            return error(toolInput, null);
        }
        try {
            return delegate.call(toolInput, toolContext);
        } catch (RuntimeException e) {
            if (!isJsonParseFailure(e)) {
                return error(toolInput, e.getMessage());
            }
            String repaired = tryRepair(toolInput);
            if (repaired == null) {
                return error(toolInput, null);
            }
            logger.warn("Repaired malformed JSON arguments for tool {}: {}", getToolDefinition().name(),
                    truncate(toolInput));
            try {
                return delegate.call(repaired, toolContext);
            } catch (RuntimeException e2) {
                return error(repaired, e2.getMessage());
            }
        }
    }

    private String tryRepair(String toolInput) {
        Object parsed;
        try {
            parsed = LENIENT_MAPPER.readValue(toolInput, Object.class);
        } catch (Exception e) {
            return null;
        }
        if (parsed instanceof String inner) {
            if (inner.isBlank()) {
                return null;
            }
            try {
                parsed = LENIENT_MAPPER.readValue(inner, Object.class);
            } catch (Exception e) {
                return null;
            }
        }
        if (!(parsed instanceof Map)) {
            return null;
        }
        try {
            return LENIENT_MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isJsonParseFailure(Throwable t) {
        if (JACKSON_EXCEPTION_TYPE == null) {
            return false;
        }
        Throwable current = t;
        while (current != null) {
            if (JACKSON_EXCEPTION_TYPE.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Class<?> nullableClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private String error(String toolInput, String cause) {
        String detail = (cause != null && !cause.isBlank()) ? " (%s)".formatted(truncate(cause)) : "";
        return ("Tool %s: the arguments could not be parsed as JSON%s. Return a single JSON object with only the "
                + "parameters of this tool, e.g. {\"parameterName\":\"value\"}. Do not wrap it in a quote or escape it.")
                .formatted(getToolDefinition().name(), detail);
    }

    private static String truncate(String value) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_INPUT_IN_LOG ? compact : compact.substring(0, MAX_INPUT_IN_LOG) + "...";
    }
}