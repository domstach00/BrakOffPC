package org.wodrol.brakoffpc.delivery;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record DeliveryRecord(
        String id,
        String sourceFileName,
        String status,
        Instant createdAt,
        Instant activatedAt,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        List<DeliveryItem> items
) {
    public DeliveryRecord(
            String id,
            String sourceFileName,
            String status,
            Instant createdAt,
            Instant activatedAt,
            List<DeliveryItem> items
    ) {
        this(id, sourceFileName, status, createdAt, activatedAt, null, null, null, items);
    }

    @JsonProperty("deliveryId")
    public String getDeliveryId() {
        return id;
    }
}
