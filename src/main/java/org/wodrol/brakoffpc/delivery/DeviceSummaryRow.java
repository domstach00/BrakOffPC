package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeviceSummaryRow(
        String deviceId,
        String deviceName,
        String barcode,
        String name,
        int quantity,
        String unit,
        long revision,
        String updatedAt
) {
    public DeviceSummaryRow {
        unit = MeasurementUnit.normalize(unit);
    }

    public DeviceSummaryRow(
            String deviceId,
            String deviceName,
            String barcode,
            String name,
            int quantity,
            long revision,
            String updatedAt
    ) {
        this(deviceId, deviceName, barcode, name, quantity, MeasurementUnit.DEFAULT_UNIT, revision, updatedAt);
    }
}
