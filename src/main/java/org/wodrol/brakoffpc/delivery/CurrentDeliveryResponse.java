package org.wodrol.brakoffpc.delivery;

import java.util.List;

public record CurrentDeliveryResponse(
        String deliveryId,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        List<CurrentDeliveryItemResponse> items
) {
    public CurrentDeliveryResponse(String deliveryId, List<CurrentDeliveryItemResponse> items) {
        this(deliveryId, null, null, null, items);
    }
}
