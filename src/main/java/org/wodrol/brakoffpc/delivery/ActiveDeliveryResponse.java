package org.wodrol.brakoffpc.delivery;

public record ActiveDeliveryResponse(
        String deliveryId,
        String sourceFileName,
        String activatedAt,
        int itemCount
) {
}
