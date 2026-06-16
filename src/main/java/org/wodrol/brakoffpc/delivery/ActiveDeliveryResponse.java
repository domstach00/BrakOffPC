package org.wodrol.brakoffpc.delivery;

public record ActiveDeliveryResponse(
        String deliveryId,
        String sourceFileName,
        String activatedAt,
        int itemCount,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber
) {
    public ActiveDeliveryResponse(String deliveryId, String sourceFileName, String activatedAt, int itemCount) {
        this(deliveryId, sourceFileName, activatedAt, itemCount, null, null, null);
    }
}
