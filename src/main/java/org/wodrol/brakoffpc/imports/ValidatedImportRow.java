package org.wodrol.brakoffpc.imports;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record ValidatedImportRow(
        int rowOrder,
        String barcode,
        String name,
        Integer expectedQty,
        String unit,
        ImportRowStatus status
) {
    public ValidatedImportRow {
        unit = MeasurementUnit.normalize(unit);
    }

    public ValidatedImportRow(int rowOrder, String barcode, String name, Integer expectedQty, ImportRowStatus status) {
        this(rowOrder, barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT, status);
    }

    public boolean hasCriticalError() {
        return status.critical();
    }
}
