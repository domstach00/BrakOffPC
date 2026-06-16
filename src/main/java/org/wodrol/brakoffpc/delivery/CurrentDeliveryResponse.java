package org.wodrol.brakoffpc.delivery;

import java.util.List;

public record CurrentDeliveryResponse(
        String deliveryId,
        String deliveryDisplayName,
        String sourceFileName,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        List<CurrentDeliveryItemResponse> items
) {
    public CurrentDeliveryResponse(
        String deliveryId,
        String sourceFileName,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        List<CurrentDeliveryItemResponse> items
    ) {
        this(
                deliveryId,
                deliveryDisplayName(supplierName, commercialDocumentNumber, warehouseDocumentNumber, sourceFileName),
                sourceFileName,
                supplierName,
                commercialDocumentNumber,
                warehouseDocumentNumber,
                items
        );
    }

    public CurrentDeliveryResponse(String deliveryId, List<CurrentDeliveryItemResponse> items) {
        this(deliveryId, null, null, null, null, items);
    }

    private static String deliveryDisplayName(
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber,
            String fallback
    ) {
        if (supplierName != null && !supplierName.isBlank()) {
            return supplierName;
        }
        if (commercialDocumentNumber != null && !commercialDocumentNumber.isBlank()) {
            return commercialDocumentNumber;
        }
        if (warehouseDocumentNumber != null && !warehouseDocumentNumber.isBlank()) {
            return warehouseDocumentNumber;
        }
        return fallback;
    }
}
