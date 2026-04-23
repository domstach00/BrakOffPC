package org.wodrol.brakoffpc.delivery;

public record DeviceSummaryRow(
        String deviceId,
        String deviceName,
        String barcode,
        String name,
        int quantity,
        long revision,
        String updatedAt
) {
}
