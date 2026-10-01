package org.wodrol.brakoffpc.delivery;

import java.time.Instant;

public record ItemComment(
        String commentId,
        String deliveryId,
        String barcode,
        String originalBarcode,
        String originalName,
        String deviceId,
        String deviceName,
        String text,
        String suggestedBarcode,
        String suggestedName,
        Instant createdAt
) {
    public String authorLabel() {
        return deviceName == null || deviceName.isBlank() ? deviceId : deviceName;
    }
}
