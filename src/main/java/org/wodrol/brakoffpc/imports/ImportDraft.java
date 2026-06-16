package org.wodrol.brakoffpc.imports;

import java.time.Instant;
import java.util.List;

public record ImportDraft(
        String id,
        String fileName,
        String status,
        String errorMessage,
        Instant createdAt,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber,
        List<ImportDraftItem> items
) {
    public ImportDraft(
            String id,
            String fileName,
            String status,
            String errorMessage,
            Instant createdAt,
            List<ImportDraftItem> items
    ) {
        this(id, fileName, status, errorMessage, createdAt, null, null, null, items);
    }
}
