package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;
import java.util.List;

public record DashboardRow(
        String barcode,
        String name,
        int expectedQty,
        int scannedQty,
        int difference,
        String unit,
        boolean unordered,
        List<ItemComment> comments
) {
    public DashboardRow {
        unit = MeasurementUnit.normalize(unit);
        comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public DashboardRow(String barcode, String name, int expectedQty, int scannedQty, int difference, String unit, boolean unordered) {
        this(barcode, name, expectedQty, scannedQty, difference, unit, unordered, List.of());
    }

    public DashboardRow(String barcode, String name, int expectedQty, int scannedQty, int difference, boolean unordered) {
        this(barcode, name, expectedQty, scannedQty, difference, MeasurementUnit.DEFAULT_UNIT, unordered);
    }

    public String status() {
        if (unordered) {
            return "Niezamowiony";
        }
        if (difference > 0) {
            return "Brakuje";
        }
        if (difference < 0) {
            return "Nadmiar";
        }
        return "OK";
    }
}
