package org.wodrol.brakoffpc.delivery;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
        return activate(
                draft,
                items,
                draft.supplierName(),
                draft.commercialDocumentNumber(),
                draft.warehouseDocumentNumber()
        );
    }

    @Transactional
    public DeliveryRecord activate(
            ImportDraft draft,
            List<ImportDraftItem> items,
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber
    ) {
        String deliveryId = UUID.randomUUID().toString();
        DeliveryRecord delivery = new DeliveryRecord(
                deliveryId,
                draft.fileName(),
                DeliveryStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                normalizeText(supplierName),
                normalizeText(commercialDocumentNumber),
                normalizeText(warehouseDocumentNumber),
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

    public List<DeliveryRecord> getActiveDeliveries() {
        return deliveryRepository.findActiveDeliveries();
    }

    public List<ActiveDeliveryResponse> getActiveDeliveryResponses() {
        return getActiveDeliveries().stream()
                .map(this::buildActiveDeliveryResponse)
                .toList();
    }

    public List<DeliveryMonitorResponse> getActiveDeliveryMonitorResponses() {
        return getActiveDeliveries().stream()
                .map(delivery -> new DeliveryMonitorResponse(
                        buildActiveDeliveryResponse(delivery),
                        getDashboardRows(delivery.id()),
                        getDeviceRowsForDelivery(delivery.id())
                ))
                .toList();
    }

    public Optional<CurrentDeliveryResponse> getCurrentDeliveryResponse() {
        return getActiveDelivery().map(this::buildCurrentDeliveryResponse);
    }

    public Optional<CurrentDeliveryResponse> getCurrentDeliveryResponse(String deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .filter(delivery -> DeliveryStatus.ACTIVE.equals(delivery.status()))
                .map(this::buildCurrentDeliveryResponse);
    }

    private CurrentDeliveryResponse buildCurrentDeliveryResponse(DeliveryRecord delivery) {
        Map<String, List<ItemComment>> comments = commentsByBarcode(delivery.id());
        Map<String, Integer> scannedQtyByBarcode = new LinkedHashMap<>();
        for (DeviceScanState scan : deliveryRepository.findScans(delivery.id())) {
            scannedQtyByBarcode.merge(scan.barcode(), scan.quantity(), Integer::sum);
        }

        return new CurrentDeliveryResponse(
                delivery.id(),
                delivery.sourceFileName(),
                delivery.supplierName(),
                delivery.commercialDocumentNumber(),
                delivery.warehouseDocumentNumber(),
                delivery.items().stream()
                        .map(item -> new CurrentDeliveryItemResponse(
                                item.barcode(),
                                item.name(),
                                item.expectedQty(),
                                item.unit(),
                                scannedQtyByBarcode.getOrDefault(item.barcode(), 0),
                                comments.getOrDefault(item.barcode(), List.of())))
                        .toList()
        );
    }

    public Optional<DeliveryRecord> getActiveDelivery(String deliveryId) {
        Optional<DeliveryRecord> delivery = deliveryRepository.findById(deliveryId)
                .filter(foundDelivery -> DeliveryStatus.ACTIVE.equals(foundDelivery.status()));
        if (delivery.isPresent()) {
            return delivery;
        }
        return getActiveDelivery()
                .filter(activeDelivery -> activeDelivery.id().equals(deliveryId));
    }

    private ActiveDeliveryResponse buildActiveDeliveryResponse(DeliveryRecord delivery) {
        return new ActiveDeliveryResponse(
                delivery.id(),
                delivery.sourceFileName(),
                formatForMobile(delivery.activatedAt()),
                delivery.items().size(),
                delivery.supplierName(),
                delivery.commercialDocumentNumber(),
                delivery.warehouseDocumentNumber()
        );
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

        Map<String, List<ItemComment>> comments = commentsByBarcode(delivery.id());
        return rows.values().stream()
                .map(row -> new DashboardRow(row.barcode(), row.name(), row.expectedQty(), row.scannedQty(),
                        row.difference(), row.unit(), row.unordered(), comments.getOrDefault(row.barcode(), List.of())))
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
    public void applyManualCorrections(
            List<DeliveryAdjustmentRow> rows,
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber
    ) {
        Optional<DeliveryRecord> active = deliveryRepository.findActive();
        if (active.isEmpty()) {
            throw new IllegalStateException("Brak aktywnej dostawy.");
        }

        applyManualCorrections(active.get().id(), rows, supplierName, commercialDocumentNumber, warehouseDocumentNumber);
    }

    @Transactional
    public void applyManualCorrections(String deliveryId, List<DeliveryAdjustmentRow> rows) {
        DeliveryRecord delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono dostawy do edycji."));
        applyManualCorrections(
                deliveryId,
                rows,
                delivery.supplierName(),
                delivery.commercialDocumentNumber(),
                delivery.warehouseDocumentNumber()
        );
    }

    @Transactional
    public void applyManualCorrections(
            String deliveryId,
            List<DeliveryAdjustmentRow> rows,
            String supplierName,
            String commercialDocumentNumber,
            String warehouseDocumentNumber
    ) {
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

        // Read the old associations first, so swapping two barcodes cannot mix comments.
        Map<String, String> correctedBarcodes = new LinkedHashMap<>();
        for (DeliveryAdjustmentRow row : finalRows) {
            if (row.originalBarcode() != null) {
                correctedBarcodes.put(row.originalBarcode(), row.barcode());
            }
        }
        for (ItemComment comment : deliveryRepository.findComments(deliveryId)) {
            String corrected = correctedBarcodes.get(comment.barcode());
            if (corrected != null && !corrected.equals(comment.barcode())) {
                deliveryRepository.moveComment(comment.commentId(), corrected);
            }
        }

        deliveryRepository.replaceItems(deliveryId, items);
        deliveryRepository.replaceScans(deliveryId, migratedScans);
        deliveryRepository.updateMetadata(
                deliveryId,
                normalizeText(supplierName),
                normalizeText(commercialDocumentNumber),
                normalizeText(warehouseDocumentNumber)
        );

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

    public List<ItemComment> getComments(String deliveryId) {
        if (getActiveDelivery(deliveryId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_ACTIVE");
        }
        return deliveryRepository.findComments(deliveryId);
    }

    public List<ItemComment> getDetachedComments(String deliveryId) {
        var barcodes = getDashboardRows(deliveryId).stream().map(DashboardRow::barcode)
                .collect(java.util.stream.Collectors.toSet());
        return deliveryRepository.findComments(deliveryId).stream()
                .filter(comment -> !barcodes.contains(comment.barcode())).toList();
    }

    private Map<String, List<ItemComment>> commentsByBarcode(String deliveryId) {
        return deliveryRepository.findComments(deliveryId).stream()
                .collect(java.util.stream.Collectors.groupingBy(ItemComment::barcode, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
    }

    @Transactional
    public ItemComment addComment(String deliveryId, String barcode, ItemCommentRequest request) {
        String targetBarcode = barcode.trim();
        String commentId = request.commentId().toLowerCase(java.util.Locale.ROOT);
        String deviceId = request.deviceId().trim();
        String deviceName = normalizeText(request.deviceName());
        String text = request.text().strip();
        String suggestedName = normalizeText(request.suggestedName());
        Optional<ItemComment> existing = deliveryRepository.findComment(commentId);
        if (existing.isPresent()) {
            ItemComment comment = existing.get();
            if (!comment.deliveryId().equals(deliveryId) || !comment.originalBarcode().equals(targetBarcode)
                    || !comment.deviceId().equals(deviceId) || !Objects.equals(comment.deviceName(), deviceName)
                    || !comment.text().equals(text) || !Objects.equals(comment.suggestedBarcode(), request.suggestedBarcode())
                    || !Objects.equals(comment.suggestedName(), suggestedName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "COMMENT_ID_CONFLICT");
            }
            // Retry remains safe even if the product was corrected or the delivery closed.
            return comment;
        }
        DeliveryRecord delivery = getActiveDelivery(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_ACTIVE"));
        String itemName = delivery.items().stream().filter(item -> item.barcode().equals(targetBarcode))
                .map(DeliveryItem::name).findFirst()
                .orElseGet(() -> deliveryRepository.findScans(deliveryId).stream()
                        .filter(scan -> scan.barcode().equals(targetBarcode)).map(this::fallbackItemName)
                        .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND")));
        ItemComment comment = new ItemComment(commentId, deliveryId, targetBarcode, targetBarcode, itemName,
                deviceId, deviceName, text, request.suggestedBarcode(), suggestedName, Instant.now());
        deliveryRepository.saveComment(comment);
        return comment;
    }

    public List<DeviceStateResponse> getDeviceState(String deviceId) {
        return getActiveDelivery()
                .map(delivery -> getDeviceState(delivery.id(), deviceId))
                .orElseGet(List::of);
    }

    public List<DeviceStateResponse> getDeviceState(String deliveryId, String deviceId) {
        Optional<DeliveryRecord> delivery = getActiveDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return List.of();
        }

        String normalizedDeviceId = deviceId == null ? "" : deviceId.trim();
        if (normalizedDeviceId.isEmpty()) {
            return List.of();
        }

        Map<String, DeliveryItem> itemsByBarcode = delivery.get().items().stream()
                .collect(java.util.stream.Collectors.toMap(DeliveryItem::barcode, item -> item, (left, right) -> left, LinkedHashMap::new));

        return deliveryRepository.findScans(delivery.get().id()).stream()
                .filter(scan -> scan.deviceId().equals(normalizedDeviceId))
                .map(scan -> {
                    DeliveryItem deliveryItem = itemsByBarcode.get(scan.barcode());
                    return new DeviceStateResponse(
                            delivery.get().id(),
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
        Optional<DeliveryRecord> selectedDelivery = resolveScanDelivery(request.getDeliveryId());
        if (selectedDelivery.isEmpty()) {
            log.warn("Odrzucono skan bez aktywnej dostawy deviceId={} barcode={}",
                    request.getDeviceId(), request.getBarcode());
            return new ScanUpdateResult(false, false, "NO_ACTIVE_DELIVERY", 0);
        }

        String normalizedBarcode = request.getBarcode().trim();
        List<DeliveryBarcodeMatchResponse> matchesInOtherActiveDeliveries = findMatchesInOtherActiveDeliveries(
                selectedDelivery.get().id(),
                normalizedBarcode
        );
        if (!barcodeBelongsToDelivery(selectedDelivery.get(), normalizedBarcode) && !matchesInOtherActiveDeliveries.isEmpty()) {
            String reason = matchesInOtherActiveDeliveries.size() == 1
                    ? "ITEM_BELONGS_TO_OTHER_DELIVERY"
                    : "ITEM_BELONGS_TO_MULTIPLE_DELIVERIES";
            log.warn("Odrzucono skan z innej dostawy selectedDeliveryId={} deviceId={} barcode={} dopasowania={}",
                    selectedDelivery.get().id(), request.getDeviceId(), normalizedBarcode, matchesInOtherActiveDeliveries.size());
            return new ScanUpdateResult(false, false, reason, 0, matchesInOtherActiveDeliveries);
        }

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
                selectedDelivery.get().id(),
                incoming.deviceId(),
                incoming.barcode()
        );

        if (existing.isPresent()) {
            DeviceScanState current = existing.get();
            if (isSameState(incoming, current)) {
                if (metadataChanged(incoming, current)) {
                    deliveryRepository.upsertScan(selectedDelivery.get().id(), incoming);
                    log.info("Zaktualizowano metadane skanu deliveryId={} deviceId={} barcode={} deviceName={}",
                            selectedDelivery.get().id(), incoming.deviceId(), incoming.barcode(), incoming.deviceName());
                }
                log.info("Pominięto duplikat skanu deliveryId={} deviceId={} barcode={} revision={} quantity={}",
                        selectedDelivery.get().id(), incoming.deviceId(), incoming.barcode(), incoming.revision(), current.quantity());
                return new ScanUpdateResult(true, true, null, current.quantity());
            }
            if (!isNewer(incoming, current)) {
                log.warn("Odrzucono przestarzały skan deliveryId={} deviceId={} barcode={} incomingRevision={} currentRevision={}",
                        selectedDelivery.get().id(), incoming.deviceId(), incoming.barcode(), incoming.revision(), current.revision());
                return new ScanUpdateResult(false, false, "STALE_REVISION", current.quantity());
            }
        }

        deliveryRepository.upsertScan(selectedDelivery.get().id(), incoming);
        log.info("Zapisano skan deliveryId={} deviceId={} barcode={} quantity={} revision={}",
                selectedDelivery.get().id(), incoming.deviceId(), incoming.barcode(), incoming.quantity(), incoming.revision());
        return new ScanUpdateResult(true, false, null, incoming.quantity());
    }

    @Transactional
    public void resetActiveDelivery() {
        getActiveDelivery().ifPresent(delivery -> closeDelivery(delivery.id()));
    }

    @Transactional
    public void closeDelivery(String deliveryId) {
        Optional<DeliveryRecord> delivery = getActiveDelivery(deliveryId);
        if (delivery.isEmpty()) {
            return;
        }

        String finalStatus = determineFinalStatus(delivery.get(), false);
        deliveryRepository.updateStatus(delivery.get().id(), finalStatus);
        log.info("Zakończono aktywną dostawę id={} statusKoncowy={}", delivery.get().id(), finalStatus);
    }

    public byte[] generateReportPdf() {
        return generateReportPdf(false);
    }

    public byte[] generateReportPdf(boolean includeComments) {
        DeliveryRecord active = deliveryRepository.findActive()
                .orElseThrow(() -> new IllegalStateException("Brak aktywnej dostawy."));
        return generateReportPdf(active.id(), includeComments);
    }

    public byte[] generateReportPdf(String deliveryId) {
        return generateReportPdf(deliveryId, false);
    }

    public byte[] generateReportPdf(String deliveryId, boolean includeComments) {
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
            document.add(new Paragraph("Raport dostawy", ReportFonts.font(16, Font.BOLD)));
            document.add(new Paragraph("ID wczytanej dostawy: " + delivery.id(), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph("Plik źródłowy: " + delivery.sourceFileName(), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph("Dostawca: " + reportValue(delivery.supplierName()), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph("Dokument handlowy: " + reportValue(delivery.commercialDocumentNumber()), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph("Przyjęcie magazynowe: " + reportValue(delivery.warehouseDocumentNumber()), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph("Wygenerowano: " + PolishDateTimeFormatter.timeNow(), ReportFonts.font(10, Font.NORMAL)));
            document.add(new Paragraph(" ", ReportFonts.font(10, Font.NORMAL)));

            addSection(document, "Brakujące produkty",
                    dashboardRows.stream().filter(row -> !row.unordered() && row.difference() > 0).toList());
            addSection(document, "Nadmiarowe produkty",
                    dashboardRows.stream().filter(row -> !row.unordered() && row.difference() < 0).toList());
            addSection(document, "Produkty niezamówione",
                    dashboardRows.stream().filter(DashboardRow::unordered).toList());
            if (includeComments) {
                addCommentsSection(document, deliveryRepository.findComments(deliveryId), dashboardRows);
            }
        } catch (DocumentException exception) {
            throw new IllegalStateException("Nie udało sie wygenerować raportu PDF.", exception);
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private void addSection(Document document, String title, List<DashboardRow> rows) throws DocumentException {
        Paragraph heading = new Paragraph(title, ReportFonts.font(13, Font.BOLD));
        heading.setSpacingBefore(10f);
        heading.setSpacingAfter(8f);
        document.add(heading);
        if (rows.isEmpty()) {
            Paragraph emptyState = new Paragraph("Brak pozycji.", ReportFonts.font(10, Font.NORMAL));
            emptyState.setSpacingAfter(10f);
            document.add(emptyState);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{3, 5, 2.4f, 2.8f, 2.2f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10f);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        addCell(table, "Barcode");
        addCell(table, "Nazwa");
        addCell(table, "Oczekiwane");
        addCell(table, "Zeskanowane");
        addCell(table, "Różnica");

        for (DashboardRow row : rows) {
            table.addCell(new Phrase(row.barcode(), ReportFonts.font(10, Font.NORMAL)));
            table.addCell(new Phrase(row.name(), ReportFonts.font(10, Font.NORMAL)));
            table.addCell(new Phrase(MeasurementUnit.format(row.expectedQty(), row.unit()), ReportFonts.font(10, Font.NORMAL)));
            table.addCell(new Phrase(MeasurementUnit.format(row.scannedQty(), row.unit()), ReportFonts.font(10, Font.NORMAL)));
            table.addCell(new Phrase(MeasurementUnit.format(row.difference(), row.unit()), ReportFonts.font(10, Font.NORMAL)));
        }
        document.add(table);
    }

    private void addCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, ReportFonts.font(10, Font.NORMAL)));
        cell.setNoWrap(true);
        table.addCell(cell);
    }

    private void addCommentsSection(Document document, List<ItemComment> comments, List<DashboardRow> rows)
            throws DocumentException {
        Paragraph heading = new Paragraph("Komentarze do produktów", ReportFonts.font(13, Font.BOLD));
        heading.setSpacingBefore(12);
        heading.setSpacingAfter(8);
        document.add(heading);
        if (comments.isEmpty()) {
            document.add(new Paragraph("Brak komentarzy.", ReportFonts.font(10, Font.NORMAL)));
            return;
        }
        Map<String, DashboardRow> products = rows.stream().collect(java.util.stream.Collectors.toMap(DashboardRow::barcode, row -> row));
        Map<String, List<ItemComment>> grouped = comments.stream().collect(java.util.stream.Collectors.groupingBy(
                ItemComment::barcode, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (var entry : grouped.entrySet()) {
            DashboardRow row = products.get(entry.getKey());
            String name = row != null ? row.name() : entry.getValue().getFirst().originalName() + " (pozycja usunięta)";
            Paragraph product = new Paragraph(entry.getKey() + " — " + name, ReportFonts.font(11, Font.BOLD));
            product.setSpacingBefore(10);
            document.add(product);
            for (ItemComment comment : entry.getValue()) {
                StringBuilder content = new StringBuilder(comment.authorLabel()).append(" · ")
                        .append(PolishDateTimeFormatter.format(comment.createdAt())).append("\n")
                        .append(comment.text());
                if (comment.suggestedBarcode() != null) {
                    content.append("\nProponowany barcode: ").append(comment.suggestedBarcode());
                }
                if (comment.suggestedName() != null) {
                    content.append("\nProponowana nazwa: ").append(comment.suggestedName());
                }
                content.append("\nDane w chwili zgłoszenia: ").append(comment.originalBarcode())
                        .append(" — ").append(comment.originalName());
                Paragraph text = new Paragraph(content.toString(), ReportFonts.font(10, Font.NORMAL));
                text.setSpacingBefore(4);
                text.setSpacingAfter(8);
                document.add(text);
            }
        }
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

    private String reportValue(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "-" : normalized;
    }

    private Optional<DeliveryRecord> resolveScanDelivery(String deliveryId) {
        String normalizedDeliveryId = normalizeText(deliveryId);
        if (normalizedDeliveryId == null || "default".equalsIgnoreCase(normalizedDeliveryId)) {
            return getActiveDelivery();
        }
        return getActiveDelivery(normalizedDeliveryId);
    }

    private List<DeliveryBarcodeMatchResponse> findMatchesInOtherActiveDeliveries(String selectedDeliveryId, String barcode) {
        return deliveryRepository.findActiveDeliveriesContainingBarcode(barcode).stream()
                .filter(match -> !match.deliveryId().equals(selectedDeliveryId))
                .toList();
    }

    private boolean barcodeBelongsToDelivery(DeliveryRecord delivery, String barcode) {
        return delivery.items().stream()
                .anyMatch(item -> item.barcode().equals(barcode));
    }

    private String formatForMobile(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }
        return MOBILE_DATE_FORMAT.format(timestamp);
    }
}
