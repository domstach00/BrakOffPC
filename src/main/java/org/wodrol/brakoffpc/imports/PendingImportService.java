package org.wodrol.brakoffpc.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.wodrol.brakoffpc.common.MeasurementUnit;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class PendingImportService {

    private static final Logger log = LoggerFactory.getLogger(PendingImportService.class);
    private final PdfImportService pdfImportService;
    private final PendingImportRepository pendingImportRepository;
    private final ImportValidationService importValidationService;

    public PendingImportService(
            PdfImportService pdfImportService,
            PendingImportRepository pendingImportRepository,
            ImportValidationService importValidationService
    ) {
        this.pdfImportService = pdfImportService;
        this.pendingImportRepository = pendingImportRepository;
        this.importValidationService = importValidationService;
    }

    public ImportDraft createFromPdf(MultipartFile file) {
        PdfImportResult importResult = pdfImportService.extractDeliveryData(file);
        List<ImportDraftItem> items = importResult.items();
        ImportDraft draft = new ImportDraft(
                UUID.randomUUID().toString(),
                file.getOriginalFilename() == null ? "delivery.pdf" : file.getOriginalFilename(),
                "PENDING",
                null,
                Instant.now(),
                importResult.supplierName(),
                importResult.commercialDocumentNumber(),
                importResult.warehouseDocumentNumber(),
                items
        );
        pendingImportRepository.save(draft);
        log.info("Utworzono draft importu id={} plik={} liczbaPozycji={}",
                draft.id(), draft.fileName(), items.size());
        return draft;
    }

    public ImportDraft get(String id) {
        log.debug("Pobieranie draftu importu id={}", id);
        return pendingImportRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Nie znaleziono importu."));
    }

    public List<ValidatedImportRow> validate(ImportDraft draft) {
        return importValidationService.validate(draft.items());
    }

    public List<ImportDraftItem> sanitizeRows(ImportRowForm form) {
        return IntStream.range(0, form.getRows().size())
                .mapToObj(index -> {
                    ImportRowInput row = form.getRows().get(index);
                    if (row.isDeleted()) {
                        return null;
                    }
                    return new ImportDraftItem(
                            index,
                            importValidationService.normalize(row.getBarcode()),
                            importValidationService.normalize(row.getName()),
                            parseInt(row.getExpectedQty()),
                            MeasurementUnit.normalize(row.getUnit()));
                })
                .filter(java.util.Objects::nonNull)
                .filter(item -> item.barcode() != null || item.name() != null || item.expectedQty() != null)
                .toList();
    }

    public boolean hasCriticalErrors(List<ImportDraftItem> items) {
        return importValidationService.hasCriticalErrors(importValidationService.validate(items));
    }

    public List<ValidatedImportRow> validateRows(List<ImportDraftItem> items) {
        return importValidationService.validate(items);
    }

    public void deleteDraft(String id) {
        pendingImportRepository.deleteById(id);
        log.info("Usunieto draft importu id={}", id);
    }

    private Integer parseInt(String rawValue) {
        String normalized = importValidationService.normalize(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
