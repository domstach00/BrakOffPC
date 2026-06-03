package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeliveryItem(
        String deliveryId,
        String barcode,
        String name,
        int expectedQty,
        String unit
) {
    public DeliveryItem {
        unit = MeasurementUnit.normalize(unit);
    }

    public DeliveryItem(String deliveryId, String barcode, String name, int expectedQty) {
        this(deliveryId, barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT);
    }
}
