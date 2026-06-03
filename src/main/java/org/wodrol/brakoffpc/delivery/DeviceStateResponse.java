package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record DeviceStateResponse(
        String deliveryId,
        String deviceId,
        String deviceName,
        String barcode,
        String name,
        String unit,
        int quantity,
        boolean fromDelivery,
        String updatedAt,
        long revision
) {
    public DeviceStateResponse {
        unit = MeasurementUnit.normalize(unit);
    }

    public DeviceStateResponse(
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
        this(deliveryId, deviceId, deviceName, barcode, name, MeasurementUnit.DEFAULT_UNIT, quantity, fromDelivery, updatedAt, revision);
    }
}
