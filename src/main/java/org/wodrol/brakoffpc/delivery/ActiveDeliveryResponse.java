package org.wodrol.brakoffpc.delivery;

public record ActiveDeliveryResponse(
        String deliveryId,
        String deliveryDisplayName,
        String sourceFileName,
        String activatedAt,
        int itemCount,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber
) {
    public ActiveDeliveryResponse(
            String deliveryId,
            String sourceFileName,
            String activatedAt,
            int itemCount,
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber
    ) {
        this(
                deliveryId,
                deliveryDisplayName(supplierName, commercialDocumentNumber, warehouseDocumentNumber, sourceFileName),
                sourceFileName,
                activatedAt,
                itemCount,
                supplierName,
                commercialDocumentNumber,
                warehouseDocumentNumber
        );
    }

    public ActiveDeliveryResponse(String deliveryId, String sourceFileName, String activatedAt, int itemCount) {
        this(deliveryId, sourceFileName, activatedAt, itemCount, null, null, null);
    }

    private static String deliveryDisplayName(
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber,
            String sourceFileName
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
        return sourceFileName;
    }
}
