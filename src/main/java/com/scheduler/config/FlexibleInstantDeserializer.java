package com.scheduler.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Accepts multiple date/time formats for Instant fields:
 *   "2026-03-26T10:00:00Z"                 (ISO UTC)
 *   "2026-03-26T10:00:00.000Z"             (ISO UTC with millis)
 *   "2026-03-26T00:39:00+05:30"            (ISO with offset)
 *   "2026-03-26T00:39:00.000+05:30"        (ISO with offset and millis)
 *   "2026-03-26T00:39:00.000 +0530"        (space before offset, no colon)
 *   "2026-03-26 00:39:00"                  (no T, treated as UTC)
 *   "2026-03-26 00:39:00.000 +0530"        (space-separated with offset)
 *   1774656600000                           (epoch millis as number)
 */
public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    private static final DateTimeFormatter FLEXIBLE_ZONED = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .optionalStart().appendLiteral(' ').optionalEnd()
            .optionalStart().appendOffset("+HHmm", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .parseDefaulting(ChronoField.OFFSET_SECONDS, 0)
            .toFormatter();

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText().trim();

        // Epoch millis as a string
        if (text.matches("^\\d+$")) {
            return Instant.ofEpochMilli(Long.parseLong(text));
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(text, FLEXIBLE_ZONED);
            return zdt.toInstant();
        } catch (DateTimeParseException ignored) {
        }

        try {
            String normalized = text.replace(' ', 'T');
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]"));
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }

        throw new IOException("Unable to parse Instant from: '" + text
                + "'. Accepted formats: ISO-8601 (2026-03-26T10:00:00Z), "
                + "offset (2026-03-26T00:39:00+05:30), "
                + "space-separated (2026-03-26 00:39:00.000 +0530), "
                + "local datetime as UTC (2026-03-26 00:39:00), "
                + "epoch millis (1774656600000)");
    }
}
