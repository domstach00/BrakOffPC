package org.wodrol.brakoffpc.delivery;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wodrol.brakoffpc.common.MeasurementUnit;
import org.wodrol.brakoffpc.imports.ImportDraft;
import org.wodrol.brakoffpc.imports.ImportDraftItem;
import org.wodrol.brakoffpc.web.PolishDateTimeFormatter;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final DateTimeFormatter MOBILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .withZone(ZoneOffset.UTC);
    private final DeliveryRepository deliveryRepository;

    public DeliveryService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public DeliveryRecord activate(ImportDraft draft, List<ImportDraftItem> items) {
        closeActiveDeliveryForReplacement();
        String deliveryId = UUID.randomUUID().toString();
        DeliveryRecord delivery = new DeliveryRecord(
                deliveryId,
                draft.fileName(),
                DeliveryStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                items.stream()
                        .map(item -> new DeliveryItem(deliveryId, item.barcode(), item.name(), item.expectedQty(), item.unit()))
                        .toList()
        );
        deliveryRepository.save(delivery);
        log.info("Aktywowano dostawę id={} plik={} liczbaPozycji={}",
                delivery.id(), delivery.sourceFileName(), delivery.items().size());
        return delivery;
    }

    public Optional<DeliveryRecord> getActiveDelivery() {
        return deliveryRepository.findActive();
    }

    public Optional<CurrentDeliveryResponse> getCurrentDeliveryResponse() {
        return getActiveDelivery().map(delivery -> {
            Map<String, Integer> scannedQtyByBarcode = new LinkedHashMap<>();
            for (DeviceScanState scan : deliveryRepository.findScans(delivery.id())) {
                scannedQtyByBarcode.merge(scan.barcode(), scan.quantity(), Integer::sum);
            }

            return new CurrentDeliveryResponse(
                    delivery.id(),
                    delivery.items().stream()
                            .map(item -> new CurrentDeliveryItemResponse(
                                    item.barcode(),
                                    item.name(),
                                    item.expectedQty(),
                                    item.unit(),
                                    scannedQtyByBarcode.getOrDefault(item.barcode(), 0)))
                            .toList()
            );
        });
    }

    public List<DashboardRow> getDashboardRows() {
        return getActiveDelivery()
                .map(this::buildDashboardRows)
                .orElseGet(List::of);
    }

    public List<DashboardRow> getDashboardRows(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .map(this::buildDashboardRows)
                .orElseGet(List::of);
    }

    public List<DeliveryArchiveSummary> getArchivedDeliveries() {
        return deliveryRepository.findArchived();
    }

    public Optional<DeliveryRecord> getDelivery(String deliveryId) {
        return deliveryRepository.findById(deliveryId);
    }

    public int purgeArchivedDeliveriesOlderThanTwoMonths() {
        return deliveryRepository.deleteArchivedOlderThan(OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2).toInstant());
    }

    @Transactional
    public void deleteArchivedDeliveries(List<String> ids) {
        List<String> normalizedIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        deliveryRepository.deleteDeliveries(normalizedIds);
        log.info("Usunięto z archiwum liczbaDostaw={}", normalizedIds.size());
    }

    public List<DeviceSummaryRow> getDeviceRows() {
        return getActiveDelivery()
                .map(this::buildDeviceRows)
                .orElseGet(List::of);
    }

    public List<DeviceSummaryRow> getDeviceRowsForDelivery(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .map(this::buildDeviceRows)
                .orElseGet(List::of);
    }

    private List<DashboardRow> buildDashboardRows(DeliveryRecord delivery) {
        Map<String, DashboardRow> rows = new LinkedHashMap<>();
        for (DeliveryItem item : delivery.items()) {
            rows.put(item.barcode(), new DashboardRow(
                    item.barcode(),
                    item.name(),
                    item.expectedQty(),
                    0,
                    item.expectedQty(),
                    item.unit(),
                    false
            ));
        }

        for (DeviceScanState scan : deliveryRepository.findScans(delivery.id())) {
            DashboardRow existing = rows.get(scan.barcode());
            if (existing != null) {
                int scanned = existing.scannedQty() + scan.quantity();
                rows.put(scan.barcode(), new DashboardRow(
                        existing.barcode(),
                        existing.name(),
                        existing.expectedQty(),
                        scanned,
                        existing.expectedQty() - scanned,
                        existing.unit(),
                        false
                ));
            } else {
                rows.put(scan.barcode(), new DashboardRow(
                        scan.barcode(),
                        fallbackItemName(scan),
                        0,
                        scan.quantity(),
                        -scan.quantity(),
                        MeasurementUnit.DEFAULT_UNIT,
                        true
                ));
            }
        }

        return rows.values().stream()
                .sorted(Comparator.comparing(DashboardRow::barcode))
                .toList();
    }

    private List<DeviceSummaryRow> buildDeviceRows(DeliveryRecord delivery) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        for (DeliveryItem item : delivery.items()) {
            names.put(item.barcode(), item.name());
            units.put(item.barcode(), item.unit());
        }

        List<DeviceScanState> scans = deliveryRepository.findScans(delivery.id());
        Map<String, String> deviceLabels = resolveDeviceLabels(scans);

        return scans.stream()
                .map(scan -> new DeviceSummaryRow(
                        scan.deviceId(),
                        deviceLabels.getOrDefault(scan.deviceId(), scan.deviceId()),
                        scan.barcode(),
                        names.getOrDefault(scan.barcode(), fallbackItemName(scan)),
                        scan.quantity(),
                        units.getOrDefault(scan.barcode(), MeasurementUnit.DEFAULT_UNIT),
                        scan.revision(),
                        PolishDateTimeFormatter.format(scan.updatedAt())))
                .toList();
    }

    public List<DeviceSummaryRow> getDeviceRows(String deviceId) {
        String normalizedDeviceId = deviceId == null ? "" : deviceId.trim();
        if (normalizedDeviceId.isEmpty()) {
            return List.of();
        }

        return getDeviceRows().stream()
                .filter(row -> row.deviceId().equals(normalizedDeviceId))
                .toList();
    }

    @Transactional
    public void applyManualCorrections(List<DeliveryAdjustmentRow> rows) {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            throw new IllegalStateException("Brak aktywnej dostawy.");
        }

        applyManualCorrections(active.get().id(), rows);
    }

    @Transactional
    public void applyManualCorrections(String deliveryId, List<DeliveryAdjustmentRow> rows) {
        DeliveryRecord delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono dostawy do edycji."));

        List<DeliveryAdjustmentRow> normalizedRows = rows == null ? List.of() : rows.stream()
                .map(this::normalizeAdjustmentRow)
                .filter(Objects::nonNull)
                .filter(this::shouldKeepAdjustmentRow)
                .toList();

        validateAdjustmentRows(normalizedRows);

        List<DeviceScanState> currentScans = deliveryRepository.findScans(deliveryId);
        Map<String, List<DeviceScanState>> scansByBarcode = currentScans.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        DeviceScanState::barcode,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));

        List<DeliveryAdjustmentRow> finalRows = normalizedRows.stream()
                .filter(row -> !row.deleted())
                .toList();
        List<DeliveryItem> items = finalRows.stream()
                .map(row -> new DeliveryItem(deliveryId, row.barcode(), row.name(), row.expectedQty(), row.unit()))
                .toList();
        List<DeviceScanState> migratedScans = rebuildScans(finalRows, scansByBarcode);

        deliveryRepository.replaceItems(deliveryId, items);
        deliveryRepository.replaceScans(deliveryId, migratedScans);

        long deletedCount = normalizedRows.stream().filter(DeliveryAdjustmentRow::deleted).count();
        long addedCount = normalizedRows.stream().filter(row -> !row.deleted() && row.originalBarcode() == null).count();
        long renamedCount = normalizedRows.stream()
                .filter(row -> !row.deleted() && row.originalBarcode() != null && !row.originalBarcode().equals(row.barcode()))
                .count();
        log.info("Zapisano ręczną korektę dostawy id={} status={} liczbaPozycji={} liczbaUsunietych={} liczbaNowych={} liczbaZmienionychBarcode={}",
                deliveryId, delivery.status(), items.size(), deletedCount, addedCount, renamedCount);
    }

    @Transactional
    public DeliveryRecord continueArchivedDelivery(String deliveryId) {
        if (deliveryRepository.findActive().isPresent()) {
            throw new IllegalStateException("Najpierw zakończ lub usuń aktywną dostawę.");
        }

        DeliveryRecord delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono archiwalnej dostawy."));
        if (DeliveryStatus.ACTIVE.equals(delivery.status())) {
            throw new IllegalStateException("Ta dostawa jest już aktywna.");
        }

        deliveryRepository.activateArchived(deliveryId, Instant.now());
        DeliveryRecord activated = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Nie udało się aktywować wybranej dostawy."));
        log.info("Przywrócono archiwalną dostawę do pracy id={} plik={} liczbaPozycji={}",
                activated.id(), activated.sourceFileName(), activated.items().size());
        return activated;
    }

    public List<DeviceStateResponse> getDeviceState(String deviceId) {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            return List.of();
        }

        String normalizedDeviceId = deviceId == null ? "" : deviceId.trim();
        if (normalizedDeviceId.isEmpty()) {
            return List.of();
        }

        Map<String, DeliveryItem> itemsByBarcode = active.get().items().stream()
                .collect(java.util.stream.Collectors.toMap(DeliveryItem::barcode, item -> item, (left, right) -> left, LinkedHashMap::new));

        return deliveryRepository.findScans(active.get().id()).stream()
                .filter(scan -> scan.deviceId().equals(normalizedDeviceId))
                .map(scan -> {
                    DeliveryItem deliveryItem = itemsByBarcode.get(scan.barcode());
                    return new DeviceStateResponse(
                            active.get().id(),
                            scan.deviceId(),
                            scan.deviceName(),
                            scan.barcode(),
                            deliveryItem != null ? deliveryItem.name() : fallbackItemName(scan),
                            deliveryItem != null ? deliveryItem.unit() : MeasurementUnit.DEFAULT_UNIT,
                            scan.quantity(),
                            deliveryItem != null,
                            formatForMobile(scan.updatedAt()),
                            scan.revision()
                    );
                })
                .toList();
    }

    @Transactional
    public ScanUpdateResult applyScanUpdate(ScanUpdateRequest request) {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            log.warn("Odrzucono skan bez aktywnej dostawy deviceId={} barcode={}",
                    request.getDeviceId(), request.getBarcode());
            return new ScanUpdateResult(false, false, "NO_ACTIVE_DELIVERY", 0);
        }

        String requestedDeliveryId = normalizeDeliveryId(request.getDeliveryId(), active.get().id());
        if (!active.get().id().equals(requestedDeliveryId)) {
            log.warn("Odrzucono skan z niezgodnym deliveryId deviceId={} barcode={} requestDeliveryId={} activeDeliveryId={}",
                    request.getDeviceId(), request.getBarcode(), request.getDeliveryId(), active.get().id());
            return new ScanUpdateResult(false, false, "DELIVERY_MISMATCH", 0);
        }

        String normalizedBarcode = request.getBarcode().trim();
        DeviceScanState incoming = new DeviceScanState(
                request.getDeviceId().trim(),
                request.getDeviceName() == null ? null : request.getDeviceName().trim(),
                normalizedBarcode,
                request.getName() == null ? null : request.getName().trim(),
                request.getQuantity(),
                request.getRevision(),
                request.getUpdatedAt()
        );

        Optional<DeviceScanState> existing = deliveryRepository.findScan(
                active.get().id(),
                incoming.deviceId(),
                incoming.barcode()
        );

        if (existing.isPresent()) {
            DeviceScanState current = existing.get();
            if (isSameState(incoming, current)) {
                if (metadataChanged(incoming, current)) {
                    deliveryRepository.upsertScan(active.get().id(), incoming);
                    log.info("Zaktualizowano metadane skanu deliveryId={} deviceId={} barcode={} deviceName={}",
                            active.get().id(), incoming.deviceId(), incoming.barcode(), incoming.deviceName());
                }
                log.info("Pominięto duplikat skanu deliveryId={} deviceId={} barcode={} revision={} quantity={}",
                        active.get().id(), incoming.deviceId(), incoming.barcode(), incoming.revision(), current.quantity());
                return new ScanUpdateResult(true, true, null, current.quantity());
            }
            if (!isNewer(incoming, current)) {
                log.warn("Odrzucono przestarzały skan deliveryId={} deviceId={} barcode={} incomingRevision={} currentRevision={}",
                        active.get().id(), incoming.deviceId(), incoming.barcode(), incoming.revision(), current.revision());
                return new ScanUpdateResult(false, false, "STALE_REVISION", current.quantity());
            }
        }

        deliveryRepository.upsertScan(active.get().id(), incoming);
        log.info("Zapisano skan deliveryId={} deviceId={} barcode={} quantity={} revision={}",
                active.get().id(), incoming.deviceId(), incoming.barcode(), incoming.quantity(), incoming.revision());
        return new ScanUpdateResult(true, false, null, incoming.quantity());
    }

    @Transactional
    public void resetActiveDelivery() {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            return;
        }

        String finalStatus = determineFinalStatus(active.get(), false);
        deliveryRepository.updateStatus(active.get().id(), finalStatus);
        log.info("Zakończono aktywną dostawę id={} statusKoncowy={}", active.get().id(), finalStatus);
    }

    public byte[] generateReportPdf() {
        DeliveryRecord active = deliveryRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("Brak aktywnej dostawy."));
        return generateReportPdf(active.id());
    }

    public byte[] generateReportPdf(String deliveryId) {
        DeliveryRecord delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono dostawy do raportu."));
        List<DashboardRow> dashboardRows = getDashboardRows(deliveryId);
        log.info("Generowanie raportu PDF dla dostawy id={} liczbaPozycji={}",
                delivery.id(), dashboardRows.size());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();
            document.add(new Paragraph("Raport dostawy", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
            document.add(new Paragraph("ID wczytanej dostawy: " + delivery.id()));
            document.add(new Paragraph("Plik źródłowy: " + delivery.sourceFileName()));
            document.add(new Paragraph("Wygenerowano: " + PolishDateTimeFormatter.timeNow()));
            document.add(new Paragraph(" "));

            addSection(document, "Brakujące produkty",
                    dashboardRows.stream().filter(row -> !row.unordered() && row.difference() > 0).toList());
            addSection(document, "Nadmiarowe produkty",
                    dashboardRows.stream().filter(row -> !row.unordered() && row.difference() < 0).toList());
            addSection(document, "Produkty niezamówione",
                    dashboardRows.stream().filter(DashboardRow::unordered).toList());
        } catch (DocumentException exception) {
            throw new IllegalStateException("Nie udało sie wygenerować raportu PDF.", exception);
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private void addSection(Document document, String title, List<DashboardRow> rows) throws DocumentException {
        Paragraph heading = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
        heading.setSpacingBefore(10f);
        heading.setSpacingAfter(8f);
        document.add(heading);
        if (rows.isEmpty()) {
            Paragraph emptyState = new Paragraph("Brak pozycji.");
            emptyState.setSpacingAfter(10f);
            document.add(emptyState);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{3, 5, 2.4f, 2.8f, 2.2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        addCell(table, "Barcode");
        addCell(table, "Nazwa");
        addCell(table, "Oczekiwane");
        addCell(table, "Zeskanowane");
        addCell(table, "Różnica");

        for (DashboardRow row : rows) {
            table.addCell(new Phrase(row.barcode()));
            table.addCell(new Phrase(row.name()));
            table.addCell(new Phrase(MeasurementUnit.format(row.expectedQty(), row.unit())));
            table.addCell(new Phrase(MeasurementUnit.format(row.scannedQty(), row.unit())));
            table.addCell(new Phrase(MeasurementUnit.format(row.difference(), row.unit())));
        }
        document.add(table);
    }

    private void addCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value));
        cell.setNoWrap(true);
        table.addCell(cell);
    }

    private boolean isNewer(DeviceScanState incoming, DeviceScanState existing) {
        if (incoming.revision() > existing.revision()) {
            return true;
        }
        return incoming.revision() == existing.revision() && incoming.updatedAt().isAfter(existing.updatedAt());
    }

    private boolean isSameState(DeviceScanState incoming, DeviceScanState existing) {
        return incoming.revision() == existing.revision()
                && incoming.quantity() == existing.quantity()
                && incoming.updatedAt().equals(existing.updatedAt());
    }

    private boolean metadataChanged(DeviceScanState incoming, DeviceScanState existing) {
        return !Objects.equals(normalizeText(incoming.deviceName()), normalizeText(existing.deviceName()))
                || !Objects.equals(normalizeText(incoming.itemName()), normalizeText(existing.itemName()));
    }

    private void closeActiveDeliveryForReplacement() {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            return;
        }

        String finalStatus = determineFinalStatus(active.get(), true);
        deliveryRepository.updateStatus(active.get().id(), finalStatus);
        log.info("Zamknięto aktywną dostawę przed aktywacją kolejnej id={} statusKoncowy={}",
                active.get().id(), finalStatus);
    }

    private String determineFinalStatus(DeliveryRecord delivery, boolean replacedByNewDelivery) {
        if (isDeliveryFinished(delivery)) {
            return DeliveryStatus.FINISHED;
        }
        return replacedByNewDelivery ? DeliveryStatus.REPLACED : DeliveryStatus.ARCHIVED;
    }

    private boolean isDeliveryFinished(DeliveryRecord delivery) {
        Map<String, Integer> scannedQtyByBarcode = new LinkedHashMap<>();
        for (DeviceScanState scan : deliveryRepository.findScans(delivery.id())) {
            scannedQtyByBarcode.merge(scan.barcode(), scan.quantity(), Integer::sum);
        }

        for (DeliveryItem item : delivery.items()) {
            if (scannedQtyByBarcode.getOrDefault(item.barcode(), 0) < item.expectedQty()) {
                return false;
            }
        }
        return true;
    }

    private String fallbackItemName(DeviceScanState scan) {
        return scan.itemName() == null || scan.itemName().isBlank() ? "Produkt spoza listy" : scan.itemName();
    }

    private Map<String, String> resolveDeviceLabels(List<DeviceScanState> scans) {
        Map<String, DeviceScanState> latestNamedScanByDevice = new LinkedHashMap<>();
        for (DeviceScanState scan : scans) {
            if (scan.deviceName() == null || scan.deviceName().isBlank()) {
                continue;
            }

            DeviceScanState existing = latestNamedScanByDevice.get(scan.deviceId());
            if (existing == null || scan.updatedAt().isAfter(existing.updatedAt())) {
                latestNamedScanByDevice.put(scan.deviceId(), scan);
            }
        }

        Map<String, String> labels = new LinkedHashMap<>();
        for (DeviceScanState scan : scans) {
            DeviceScanState latestNamedScan = latestNamedScanByDevice.get(scan.deviceId());
            labels.put(scan.deviceId(), latestNamedScan != null ? latestNamedScan.deviceName() : scan.deviceId());
        }
        return labels;
    }

    private DeliveryAdjustmentRow normalizeAdjustmentRow(DeliveryAdjustmentRow row) {
        if (row == null) {
            return null;
        }
        String originalBarcode = normalizeText(row.originalBarcode());
        String barcode = normalizeText(row.barcode());
        String name = normalizeText(row.name());
        return new DeliveryAdjustmentRow(originalBarcode, barcode, name, row.expectedQty(), row.unit(), row.deleted());
    }

    private boolean shouldKeepAdjustmentRow(DeliveryAdjustmentRow row) {
        if (row.originalBarcode() != null) {
            return true;
        }
        if (row.deleted()) {
            return false;
        }
        return row.barcode() != null || row.name() != null;
    }

    private void validateAdjustmentRows(List<DeliveryAdjustmentRow> rows) {
        LinkedHashSet<String> seenOriginalBarcodes = new LinkedHashSet<>();
        LinkedHashSet<String> seenBarcodes = new LinkedHashSet<>();
        for (DeliveryAdjustmentRow row : rows) {
            if (row.originalBarcode() != null && !seenOriginalBarcodes.add(row.originalBarcode())) {
                throw new IllegalArgumentException("Wykryto duplikat źródłowego barcode w korekcie dostawy.");
            }
            if (row.deleted()) {
                continue;
            }
            if (row.barcode() == null || row.barcode().isBlank()) {
                throw new IllegalArgumentException("Każdy zapisany wiersz musi mieć barcode.");
            }
            if (!row.barcode().chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Barcode musi składać się z cyfr.");
            }
            if (row.name() == null || row.name().isBlank()) {
                throw new IllegalArgumentException("Każdy zapisany wiersz musi mieć nazwę.");
            }
            if (row.expectedQty() < 0) {
                throw new IllegalArgumentException("Oczekiwana ilość nie może być ujemna.");
            }
            if (row.unit() == null || row.unit().isBlank()) {
                throw new IllegalArgumentException("Jednostka miary nie może być pusta.");
            }
            if (!seenBarcodes.add(row.barcode())) {
                throw new IllegalArgumentException("Wykryto duplikat barcode w korekcie dostawy.");
            }
        }
    }

    private List<DeviceScanState> rebuildScans(
            List<DeliveryAdjustmentRow> finalRows,
            Map<String, List<DeviceScanState>> scansByBarcode
    ) {
        List<DeviceScanState> scans = new ArrayList<>();
        for (DeliveryAdjustmentRow row : finalRows) {
            if (row.originalBarcode() == null) {
                continue;
            }
            for (DeviceScanState scan : scansByBarcode.getOrDefault(row.originalBarcode(), List.of())) {
                scans.add(new DeviceScanState(
                        scan.deviceId(),
                        scan.deviceName(),
                        row.barcode(),
                        row.name(),
                        scan.quantity(),
                        scan.revision(),
                        scan.updatedAt()
                ));
            }
        }
        return scans;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeDeliveryId(String deliveryId, String activeDeliveryId) {
        if (deliveryId == null) {
            return "";
        }
        String normalized = deliveryId.trim();
        if (normalized.isEmpty() || "default".equalsIgnoreCase(normalized)) {
            return activeDeliveryId;
        }
        return normalized;
    }

    private String formatForMobile(Instant timestamp) {
        return MOBILE_DATE_FORMAT.format(timestamp);
    }
}
