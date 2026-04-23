package org.wodrol.brakoffpc.imports;

public record ValidatedImportRow(
        int rowOrder,
        String barcode,
        String name,
        Integer expectedQty,
        ImportRowStatus status
) {
    public boolean hasCriticalError() {
        return status.critical();
    }
}
