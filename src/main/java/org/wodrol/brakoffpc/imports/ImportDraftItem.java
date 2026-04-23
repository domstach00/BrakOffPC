package org.wodrol.brakoffpc.imports;

public record ImportDraftItem(
        int rowOrder,
        String barcode,
        String name,
        Integer expectedQty
) {
}
