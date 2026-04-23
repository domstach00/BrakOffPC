package org.wodrol.brakoffpc.delivery;

public record CurrentDeliveryItemResponse(
        String barcode,
        String name,
        int expectedQty,
        int scannedQty
) {
}
