package org.wodrol.brakoffpc.delivery;

import java.time.Instant;

public record DeviceScanState(
        String deviceId,
        String deviceName,
        String barcode,
        String itemName,
        int quantity,
        long revision,
        Instant updatedAt
) {
}
