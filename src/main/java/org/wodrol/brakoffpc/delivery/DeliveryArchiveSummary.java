package org.wodrol.brakoffpc.delivery;

import java.time.Instant;

public record DeliveryArchiveSummary(
        String id,
        String sourceFileName,
        String status,
        Instant createdAt,
        Instant activatedAt,
        int itemCount
) {
}
