package org.wodrol.brakoffpc.delivery;

public record DeliveryItem(
        String deliveryId,
        String barcode,
        String name,
        int expectedQty
) {
}
