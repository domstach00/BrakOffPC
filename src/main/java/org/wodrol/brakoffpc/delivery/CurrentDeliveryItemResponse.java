package org.wodrol.brakoffpc.delivery;

import org.wodrol.brakoffpc.common.MeasurementUnit;
import java.util.List;

public record CurrentDeliveryItemResponse(
        String barcode,
        String name,
        int expectedQty,
        String unit,
        int scannedQty,
        List<ItemComment> comments
) {
    public CurrentDeliveryItemResponse {
        unit = MeasurementUnit.normalize(unit);
        comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public CurrentDeliveryItemResponse(String barcode, String name, int expectedQty, String unit, int scannedQty) {
        this(barcode, name, expectedQty, unit, scannedQty, List.of());
    }

    public CurrentDeliveryItemResponse(String barcode, String name, int expectedQty, int scannedQty) {
        this(barcode, name, expectedQty, MeasurementUnit.DEFAULT_UNIT, scannedQty);
    }
}
