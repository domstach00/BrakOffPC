package org.wodrol.brakoffpc.imports;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ImportValidationService {

    public List<ValidatedImportRow> validate(List<ImportDraftItem> items) {
        Map<String, Integer> barcodeCounts = new HashMap<>();
        for (ImportDraftItem item : items) {
            String normalizedBarcode = normalize(item.barcode());
            if (normalizedBarcode != null && normalizedBarcode.chars().allMatch(Character::isDigit)) {
                barcodeCounts.merge(normalizedBarcode, 1, Integer::sum);
            }
        }

        return items.stream()
                .map(item -> new ValidatedImportRow(
                        item.rowOrder(),
                        normalize(item.barcode()),
                        normalize(item.name()),
                        item.expectedQty(),
                        determineStatus(item, barcodeCounts)))
                .toList();
    }

    private ImportRowStatus determineStatus(ImportDraftItem item, Map<String, Integer> barcodeCounts) {
        String barcode = normalize(item.barcode());
        if (barcode == null) {
            return ImportRowStatus.MISSING_BARCODE;
        }
        if (!barcode.chars().allMatch(Character::isDigit)) {
            return ImportRowStatus.INVALID_BARCODE;
        }
        if (normalize(item.name()) == null) {
            return ImportRowStatus.INVALID_NAME;
        }
        if (item.expectedQty() == null || item.expectedQty() < 0) {
            return ImportRowStatus.INVALID_QUANTITY;
        }
        if (Objects.requireNonNullElse(barcodeCounts.get(barcode), 0) > 1) {
            return ImportRowStatus.DUPLICATE_BARCODE;
        }
        return ImportRowStatus.OK;
    }

    public boolean hasCriticalErrors(List<ValidatedImportRow> rows) {
        return rows.stream().anyMatch(ValidatedImportRow::hasCriticalError);
    }

    public String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
