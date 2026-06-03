package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeliveryAdjustmentRow(
        String originalBarcode,
        String barcode,
        String name,
        int expectedQty,
        String unit,
        boolean deleted
) {
    public DeliveryAdjustmentRow {
        unit = MeasurementUnit.normalize(unit);
    }

    public DeliveryAdjustmentRow(String originalBarcode, String barcode, String name, int expectedQty, boolean deleted) {
        this(originalBarcode, barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT, deleted);
    }
}
