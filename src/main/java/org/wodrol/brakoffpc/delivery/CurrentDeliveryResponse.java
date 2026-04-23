package org.wodrol.brakoffpc.delivery;

import java.util.List;

public record CurrentDeliveryResponse(
        String deliveryId,
        List<CurrentDeliveryItemResponse> items
) {
}
