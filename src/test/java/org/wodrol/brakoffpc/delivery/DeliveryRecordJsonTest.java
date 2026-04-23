package org.wodrol.brakoffpc.delivery;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryRecordJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesDeliveryIdAliasForApiClients() throws Exception {
        DeliveryRecord record = new DeliveryRecord(
                "delivery-123",
                "source.pdf",
                "ACTIVE",
                null,
                null,
                List.of(new DeliveryItem("delivery-123", "111", "Produkt A", 2))
        );

        String json = objectMapper.writeValueAsString(record);

        assertEquals("delivery-123", objectMapper.readTree(json).get("deliveryId").asText());
        assertEquals("delivery-123", objectMapper.readTree(json).get("items").get(0).get("deliveryId").asText());
    }
}
