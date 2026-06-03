package org.wodrol.brakoffpc.imports;

import org.wodrol.brakoffpc.common.MeasurementUnit;

public record ImportDraftItem(
        int rowOrder,
        String barcode,
        String name,
        Integer expectedQty,
        String unit
) {
    public ImportDraftItem {
        unit = MeasurementUnit.normalize(unit);
    }

    public ImportDraftItem(int rowOrder, String barcode, String name, Integer expectedQty) {
        this(rowOrder, barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT);
    }
}
