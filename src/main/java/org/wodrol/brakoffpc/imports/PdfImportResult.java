package org.wodrol.brakoffpc.imports;

import java.util.List;

public record PdfImportResult(
        List<ImportDraftItem> items,
        String supplierName,
        String commercialDocumentNumber,
        String warehouseDocumentNumber
) {
    public PdfImportResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
