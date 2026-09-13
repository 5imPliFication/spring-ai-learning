package com.example.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class DateTimeTools {

    private static final DateTimeFormatter HUMAN_DATE =
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    private static final DateTimeFormatter HUMAN_TIME =
            DateTimeFormatter.ofPattern("h:mm a z");

    @Tool(description = "Get today's date, e.g. 2026-09-12 (Saturday, September 12, 2026)")
    public String getCurrentDate() {
        LocalDate today = LocalDate.now();
        log.info("Get Current Date HIT!!");
        return today + " (" + today.format(HUMAN_DATE) + ")";
    }

    @Tool(description = "Get the current time for a given IANA timezone, e.g. UTC, Asia/Tokyo, America/New_York")
    public String getCurrentTime(
            @ToolParam(description = "IANA timezone ID; use UTC unless the user specifies otherwise") String timezone) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        log.info("Get Current UTC Time HIT!!");
        return now.format(HUMAN_TIME) + " (" + now.toInstant() + ")";
    }
}