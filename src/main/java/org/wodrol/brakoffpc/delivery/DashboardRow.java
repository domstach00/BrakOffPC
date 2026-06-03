package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DashboardRow(
        String barcode,
        String name,
        int expectedQty,
        int scannedQty,
        int difference,
        String unit,
        boolean unordered
) {
    public DashboardRow {
        unit = MeasurementUnit.normalize(unit);
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
