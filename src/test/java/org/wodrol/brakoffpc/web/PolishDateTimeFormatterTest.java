package org.wodrol.brakoffpc.web;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PolishDateTimeFormatterTest {

    @Test
    void formatsCommentTimestampWithoutSecondsInPolishTimeZone() {
        assertEquals(
                "2026-10-01 11:15",
                PolishDateTimeFormatter.formatWithoutSeconds(Instant.parse("2026-10-01T09:15:47Z"))
        );
    }
}
