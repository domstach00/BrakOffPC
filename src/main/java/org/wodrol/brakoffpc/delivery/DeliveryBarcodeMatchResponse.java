package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeliveryBarcodeMatchResponse(
        String deliveryId,
        String deliveryDisplayName,
        String sourceFileName,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        String barcode,
        String name,
        int expectedQty,
        String unit
) {
    public DeliveryBarcodeMatchResponse {
        unit = MeasurementUnit.normalize(unit);
    }

    public DeliveryBarcodeMatchResponse(
            String deliveryId,
            String sourceFileName,
            String barcode,
            String name,
            int expectedQty,
            String unit
    ) {
        this(deliveryId, sourceFileName, sourceFileName, null, null, null, barcode, name, expectedQty, unit);
    }
}
