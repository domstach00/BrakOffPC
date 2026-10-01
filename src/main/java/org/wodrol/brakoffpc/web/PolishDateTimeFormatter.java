package org.wodrol.brakoffpc.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class PolishDateTimeFormatter {

    private static final ZoneId POLAND_ZONE = ZoneId.of("Europe/Warsaw");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(POLAND_ZONE);
    private static final DateTimeFormatter DISPLAY_WITHOUT_SECONDS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(POLAND_ZONE);

    private PolishDateTimeFormatter() {
    }

    public static String format(Instant timestamp) {
        if (timestamp == null) {
            return "";
        }
        return DISPLAY_FORMAT.format(timestamp);
    }

    public static String timeNow() {
        return format(Instant.now());
    }

    public static String formatWithoutSeconds(Instant timestamp) {
        if (timestamp == null) {
            return "";
        }
        return DISPLAY_WITHOUT_SECONDS_FORMAT.format(timestamp);
    }
}
