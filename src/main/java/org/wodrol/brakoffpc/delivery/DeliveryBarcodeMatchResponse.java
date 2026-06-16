package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeliveryBarcodeMatchResponse(
        String deliveryId,
        String sourceFileName,
        String barcode,
        String name,
        int expectedQty,
        String unit
) {
    public DeliveryBarcodeMatchResponse {
        unit = MeasurementUnit.normalize(unit);
    }
}
