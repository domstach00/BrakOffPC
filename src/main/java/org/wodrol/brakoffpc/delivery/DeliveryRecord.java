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
        List<DeliveryItem> items
) {
    @JsonProperty("deliveryId")
    public String getDeliveryId() {
        return id;
    }
}
