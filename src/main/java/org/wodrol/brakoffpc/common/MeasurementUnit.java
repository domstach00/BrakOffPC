package org.wodrol.brakoffpc.common;

import java.util.Locale;

public final class MeasurementUnit {

    public static final String DEFAULT_UNIT = "szt";

    private MeasurementUnit() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return DEFAULT_UNIT;
        }

        String normalized = value
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("^[\\s\\-_|:;,.]+", "")
                .replaceAll("[\\s\\-_|:;,.]+$", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            return DEFAULT_UNIT;
        }
        if ("sz".equals(normalized) || "sztuk".equals(normalized) || "sztuki".equals(normalized)) {
            return DEFAULT_UNIT;
        }
        return normalized;
    }

    public static String format(int quantity, String unit) {
        return quantity + " " + normalize(unit);
    }
}
