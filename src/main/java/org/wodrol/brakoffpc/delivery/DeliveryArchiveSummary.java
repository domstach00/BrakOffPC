package org.wodrol.brakoffpc.delivery;

import java.time.Instant;

public record DeliveryArchiveSummary(
        String id,
        String sourceFileName,
        String status,
        Instant createdAt,
        Instant activatedAt,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        int itemCount
) {
    public DeliveryArchiveSummary(
            String id,
            String sourceFileName,
            String status,
            Instant createdAt,
            Instant activatedAt,
            int itemCount
    ) {
        this(id, sourceFileName, status, createdAt, activatedAt, null, null, null, itemCount);
    }
}
