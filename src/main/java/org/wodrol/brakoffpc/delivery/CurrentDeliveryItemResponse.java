package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record CurrentDeliveryItemResponse(
        String barcode,
        String name,
        int expectedQty,
        String unit,
        int scannedQty
) {
    public CurrentDeliveryItemResponse {
        unit = MeasurementUnit.normalize(unit);
    }

    public CurrentDeliveryItemResponse(String barcode, String name, int expectedQty, int scannedQty) {
        this(barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT, scannedQty);
    }
}
