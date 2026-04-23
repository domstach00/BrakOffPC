package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportValidationServiceTest {

    private final ImportValidationService validationService = new ImportValidationService();

    @Test
    void flagsDuplicateBarcodeAsCriticalError() {
        List<ValidatedImportRow> rows = validationService.validate(List.of(
                new ImportDraftItem(0, "123456", "Produkt A", 1),
                new ImportDraftItem(1, "123456", "Produkt B", 2)
        ));

        assertEquals(ImportRowStatus.DUPLICATE_BARCODE, rows.get(0).status());
        assertEquals(ImportRowStatus.DUPLICATE_BARCODE, rows.get(1).status());
        assertTrue(validationService.hasCriticalErrors(rows));
    }

    @Test
    void flagsInvalidBarcodeAndQuantity() {
        List<ValidatedImportRow> rows = validationService.validate(List.of(
                new ImportDraftItem(0, "12A45", "Produkt A", 1),
                new ImportDraftItem(1, "22222", "Produkt B", -1)
        ));

        assertEquals(ImportRowStatus.INVALID_BARCODE, rows.get(0).status());
        assertEquals(ImportRowStatus.INVALID_QUANTITY, rows.get(1).status());
    }
}
