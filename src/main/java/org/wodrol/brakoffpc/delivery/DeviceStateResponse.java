package org.wodrol.brakoffpc.delivery;

public record DeviceStateResponse(
        String deliveryId,
        String deviceId,
        String deviceName,
        String barcode,
        String name,
        int quantity,
        boolean fromDelivery,
        String updatedAt,
        long revision
) {
}
