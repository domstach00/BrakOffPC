package org.wodrol.brakoffpc.delivery;

public record DeliveryAdjustmentRow(
        String originalBarcode,
        String barcode,
        String name,
        int expectedQty,
        boolean deleted
) {
}
