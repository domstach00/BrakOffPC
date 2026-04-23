package org.wodrol.brakoffpc.imports;

import java.time.Instant;
import java.util.List;

public record ImportDraft(
        String id,
        String fileName,
        String status,
        String errorMessage,
        Instant createdAt,
        List<ImportDraftItem> items
) {
}
