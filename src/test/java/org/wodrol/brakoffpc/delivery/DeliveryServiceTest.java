package org.wodrol.brakoffpc.delivery;

import org.junit.jupiter.api.Test;
import org.wodrol.brakoffpc.imports.ImportDraft;
import org.wodrol.brakoffpc.imports.ImportDraftItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {

    @Test
    void replacesIncompleteActiveDeliveryWithStatusReplaced() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-old",
                "old.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-old", "111", "Produkt A", 5))
        );
        ImportDraft draft = new ImportDraft("draft-1", "new.pdf", "READY", null, Instant.now(), List.of());

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-old")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now())
        ));

        service.activate(draft, List.of(new ImportDraftItem(1, "222", "Produkt B", 2)));

        verify(repository).updateStatus("delivery-old", "REPLACED");
        verify(repository).save(argThat(delivery ->
                delivery.sourceFileName().equals("new.pdf") && delivery.status().equals("ACTIVE")
        ));
    }

    @Test
    void replacesCompletedActiveDeliveryWithStatusFinished() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-old",
                "old.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-old", "111", "Produkt A", 5))
        );
        ImportDraft draft = new ImportDraft("draft-1", "new.pdf", "READY", null, Instant.now(), List.of());

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-old")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 5, 1, Instant.now())
        ));

        service.activate(draft, List.of(new ImportDraftItem(1, "222", "Produkt B", 2)));

        verify(repository).updateStatus("delivery-old", "FINISHED");
    }

    @Test
    void manuallyClosingIncompleteActiveDeliveryMarksItAsArchived() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now())
        ));

        service.resetActiveDelivery();

        verify(repository).updateStatus("delivery-1", "ARCHIVED");
    }

    @Test
    void manuallyClosingCompletedActiveDeliveryMarksItAsFinished() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 5, 1, Instant.now())
        ));

        service.resetActiveDelivery();

        verify(repository).updateStatus("delivery-1", "FINISHED");
    }

    @Test
    void acceptsIdenticalRetryAsIdempotent() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "123456", "Produkt A", 4))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "123456"))
                .thenReturn(Optional.of(new DeviceScanState("device-1", "Telefon 1", "123456", "Produkt A", 3, 7, updatedAt)));

        ScanUpdateResult result = service.applyScanUpdate(request("delivery-1", "device-1", "123456", 3, 7, updatedAt));

        assertTrue(result.accepted());
        assertTrue(result.unchanged());
        assertEquals(null, result.reason());
        assertEquals(3, result.serverQuantity());
        verify(repository, never()).upsertScan(any(), any());
    }

    @Test
    void updatesDeviceNameEvenWhenScanStateIsOtherwiseIdentical() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "123456", "Produkt A", 4))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");
        ScanUpdateRequest request = request("delivery-1", "device-1", "123456", 3, 7, updatedAt);
        request.setDeviceName("Android Device2");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "123456"))
                .thenReturn(Optional.of(new DeviceScanState("device-1", "Android Device1", "123456", "Produkt A", 3, 7, updatedAt)));

        ScanUpdateResult result = service.applyScanUpdate(request);

        assertTrue(result.accepted());
        assertTrue(result.unchanged());
        assertEquals(3, result.serverQuantity());
        verify(repository).upsertScan(eq("delivery-1"), argThat(scan ->
                scan.deviceId().equals("device-1")
                        && "Android Device2".equals(scan.deviceName())
                        && scan.quantity() == 3
                        && scan.revision() == 7
        ));
    }

    @Test
    void rejectsOlderState() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of()
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "123456"))
                .thenReturn(Optional.of(new DeviceScanState("device-1", "Telefon 1", "123456", "Produkt A", 3, 7, updatedAt)));

        ScanUpdateResult result = service.applyScanUpdate(request("delivery-1", "device-1", "123456", 2, 6, updatedAt.minusSeconds(30)));

        assertFalse(result.accepted());
        assertEquals("STALE_REVISION", result.reason());
        assertEquals(3, result.serverQuantity());
        verify(repository, never()).upsertScan(any(), any());
    }

    @Test
    void acceptsScanForProductOutsideDeliveryAndKeepsItForUnorderedReporting() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");
        ScanUpdateRequest request = request("delivery-1", "device-1", "999", 2, 1, updatedAt);
        request.setName("Produkt X");
        request.setFromDelivery(false);

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "999"))
                .thenReturn(Optional.empty());
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "999", "Produkt X", 2, 1, updatedAt)
        ));

        ScanUpdateResult result = service.applyScanUpdate(request);

        assertTrue(result.accepted());
        assertFalse(result.unchanged());
        assertEquals(2, result.serverQuantity());
        verify(repository).upsertScan(eq("delivery-1"), any(DeviceScanState.class));

        DashboardRow unordered = service.getDashboardRows().stream()
                .filter(row -> row.barcode().equals("999"))
                .findFirst()
                .orElseThrow();
        assertTrue(unordered.unordered());
        assertEquals("999", unordered.barcode());
        assertEquals("Produkt X", unordered.name());
        assertEquals(-2, unordered.difference());
    }

    @Test
    void acceptsLegacyDefaultDeliveryIdForActiveDelivery() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");
        ScanUpdateRequest request = request("default", "device-1", "999", 2, 1, updatedAt);
        request.setName("Produkt X");
        request.setFromDelivery(false);

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "999"))
                .thenReturn(Optional.empty());

        ScanUpdateResult result = service.applyScanUpdate(request);

        assertTrue(result.accepted());
        assertFalse(result.unchanged());
        assertEquals(2, result.serverQuantity());
        verify(repository).upsertScan(eq("delivery-1"), any(DeviceScanState.class));
    }

    @Test
    void includesAggregatedScannedQtyInCurrentDeliveryResponse() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(
                        new DeliveryItem("delivery-1", "111", "Produkt A", 5, "kpl"),
                        new DeliveryItem("delivery-1", "222", "Produkt B", 2)
                )
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now()),
                new DeviceScanState("device-2", "Telefon 2", "111", "Produkt A", 4, 2, Instant.now()),
                new DeviceScanState("device-1", "Telefon 1", "999", "Produkt X", 2, 1, Instant.now())
        ));

        CurrentDeliveryResponse response = service.getCurrentDeliveryResponse().orElseThrow();

        assertEquals("delivery-1", response.deliveryId());
        assertEquals(2, response.items().size());
        CurrentDeliveryItemResponse firstItem = response.items().stream()
                .filter(item -> item.barcode().equals("111"))
                .findFirst()
                .orElseThrow();
        CurrentDeliveryItemResponse secondItem = response.items().stream()
                .filter(item -> item.barcode().equals("222"))
                .findFirst()
                .orElseThrow();
        assertEquals(7, firstItem.scannedQty());
        assertEquals("kpl", firstItem.unit());
        assertEquals(0, secondItem.scannedQty());
        assertEquals("szt", secondItem.unit());
    }

    @Test
    void appliesManualCorrectionsIncludingBarcodeChangeNewRowsAndDeletedRows() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findById("delivery-1")).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 4, updatedAt),
                new DeviceScanState("device-2", "Telefon 2", "222", "Do usuniecia", 1, 2, updatedAt.plusSeconds(60)),
                new DeviceScanState("device-3", "Telefon 3", "999", "Produkt spoza listy", 2, 5, updatedAt.plusSeconds(120))
        ));

        service.applyManualCorrections(List.of(
                new DeliveryAdjustmentRow("111", "333", "Produkt A poprawiony", 7, false),
                new DeliveryAdjustmentRow("999", "999", "Produkt spoza listy", 2, false),
                new DeliveryAdjustmentRow("222", "222", "Do usuniecia", 0, true),
                new DeliveryAdjustmentRow(null, "444", "Nowy produkt", 1, false)
        ));

        verify(repository).replaceItems(eq("delivery-1"), argThat(items ->
                items.size() == 3
                        && items.stream().anyMatch(item -> item.barcode().equals("333") && item.expectedQty() == 7 && item.name().equals("Produkt A poprawiony"))
                        && items.stream().anyMatch(item -> item.barcode().equals("999") && item.expectedQty() == 2)
                        && items.stream().anyMatch(item -> item.barcode().equals("444") && item.expectedQty() == 1 && item.name().equals("Nowy produkt"))
        ));
        verify(repository).replaceScans(eq("delivery-1"), argThat(scans ->
                scans.size() == 2
                        && scans.stream().anyMatch(scan ->
                        scan.deviceId().equals("device-1")
                                && scan.barcode().equals("333")
                                && scan.itemName().equals("Produkt A poprawiony")
                                && scan.quantity() == 3
                                && scan.revision() == 4
                )
                        && scans.stream().anyMatch(scan ->
                        scan.deviceId().equals("device-3")
                                && scan.barcode().equals("999")
                                && scan.itemName().equals("Produkt spoza listy")
                                && scan.quantity() == 2
                                && scan.revision() == 5
                )
        ));
    }

    @Test
    void appliesManualCorrectionsForArchivedDeliveryWithoutActiveOne() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord archivedDelivery = new DeliveryRecord(
                "delivery-archive",
                "source.pdf",
                "ARCHIVED",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-archive", "111", "Produkt A", 5))
        );
        Instant updatedAt = Instant.parse("2026-04-22T08:00:00Z");

        when(repository.findById("delivery-archive")).thenReturn(Optional.of(archivedDelivery));
        when(repository.findScans("delivery-archive")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 4, updatedAt)
        ));

        service.applyManualCorrections("delivery-archive", List.of(
                new DeliveryAdjustmentRow("111", "333", "Produkt A poprawiony", 7, false),
                new DeliveryAdjustmentRow(null, "444", "Nowy produkt", 1, false)
        ));

        verify(repository).replaceItems(eq("delivery-archive"), argThat(items ->
                items.size() == 2
                        && items.stream().anyMatch(item -> item.barcode().equals("333") && item.expectedQty() == 7)
                        && items.stream().anyMatch(item -> item.barcode().equals("444") && item.expectedQty() == 1)
        ));
        verify(repository).replaceScans(eq("delivery-archive"), argThat(scans ->
                scans.size() == 1
                        && scans.getFirst().barcode().equals("333")
                        && scans.getFirst().itemName().equals("Produkt A poprawiony")
        ));
    }

    @Test
    void rejectsManualCorrectionsWithDuplicateBarcodes() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findById("delivery-1")).thenReturn(Optional.of(activeDelivery));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.applyManualCorrections(List.of(
                        new DeliveryAdjustmentRow("111", "333", "Produkt A", 5, false),
                        new DeliveryAdjustmentRow("222", "333", "Produkt B", 1, false)
                )));

        assertEquals("Wykryto duplikat barcode w korekcie dostawy.", exception.getMessage());
    }

    @Test
    void returnsArchivedDeliveriesFromRepository() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        List<DeliveryArchiveSummary> archived = List.of(
                new DeliveryArchiveSummary("delivery-1", "a.pdf", "ARCHIVED", Instant.now(), Instant.now(), 3)
        );

        when(repository.findArchived()).thenReturn(archived);

        assertEquals(archived, service.getArchivedDeliveries());
    }

    @Test
    void deletesSelectedArchivedDeliveries() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);

        service.deleteArchivedDeliveries(List.of("delivery-1", "delivery-2", "delivery-1", " "));

        verify(repository).deleteDeliveries(List.of("delivery-1", "delivery-2"));
    }

    @Test
    void continuesArchivedDeliveryWhenNoActiveDeliveryExists() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord archivedDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ARCHIVED",
                Instant.parse("2026-04-20T08:00:00Z"),
                Instant.parse("2026-04-20T08:00:00Z"),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );
        DeliveryRecord activatedDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                archivedDelivery.createdAt(),
                Instant.parse("2026-04-23T08:00:00Z"),
                archivedDelivery.items()
        );

        when(repository.findActive()).thenReturn(Optional.empty());
        when(repository.findById("delivery-1")).thenReturn(Optional.of(archivedDelivery), Optional.of(activatedDelivery));

        DeliveryRecord result = service.continueArchivedDelivery("delivery-1");

        assertEquals("ACTIVE", result.status());
        verify(repository).activateArchived(eq("delivery-1"), any(Instant.class));
    }

    @Test
    void rejectsContinuingArchivedDeliveryWhenAnotherDeliveryIsActive() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-active",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of()
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.continueArchivedDelivery("delivery-1"));

        assertEquals("Najpierw zakończ lub usuń aktywną dostawę.", exception.getMessage());
        verify(repository, never()).activateArchived(eq("delivery-1"), any(Instant.class));
    }

    @Test
    void generatesReportPdfForArchivedDelivery() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord archivedDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ARCHIVED",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );

        when(repository.findById("delivery-1")).thenReturn(Optional.of(archivedDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now())
        ));

        byte[] pdf = service.generateReportPdf("delivery-1");

        assertTrue(pdf.length > 0);
    }

    @Test
    void aggregatesExpectedAndUnorderedRows() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(
                        new DeliveryItem("delivery-1", "111", "Produkt A", 5),
                        new DeliveryItem("delivery-1", "222", "Produkt B", 2)
                )
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now()),
                new DeviceScanState("device-2", "Telefon 2", "111", "Produkt A", 4, 2, Instant.now()),
                new DeviceScanState("device-1", "Telefon 1", "999", "Produkt X", 2, 1, Instant.now())
        ));

        List<DashboardRow> rows = service.getDashboardRows();

        DashboardRow productA = rows.stream().filter(row -> row.barcode().equals("111")).findFirst().orElseThrow();
        DashboardRow unordered = rows.stream().filter(row -> row.barcode().equals("999")).findFirst().orElseThrow();

        assertEquals(7, productA.scannedQty());
        assertEquals(-2, productA.difference());
        assertTrue(unordered.unordered());
        assertEquals("Produkt X", unordered.name());
    }

    @Test
    void savesNewerState() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of()
        );
        Instant currentUpdatedAt = Instant.parse("2026-04-22T08:00:00Z");
        Instant newUpdatedAt = Instant.parse("2026-04-22T08:05:00Z");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScan("delivery-1", "device-1", "123456"))
                .thenReturn(Optional.of(new DeviceScanState("device-1", "Telefon 1", "123456", "Produkt A", 1, 1, currentUpdatedAt)));

        ScanUpdateResult result = service.applyScanUpdate(request("delivery-1", "device-1", "123456", 2, 2, newUpdatedAt));

        assertTrue(result.accepted());
        assertFalse(result.unchanged());
        assertEquals(2, result.serverQuantity());
        verify(repository).upsertScan(eq("delivery-1"), any(DeviceScanState.class));
    }

    @Test
    void returnsRowsOnlyForSelectedDevice() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(new DeliveryItem("delivery-1", "111", "Produkt A", 5))
        );

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Telefon 1", "111", "Produkt A", 3, 1, Instant.now()),
                new DeviceScanState("device-2", "Telefon 2", "111", "Produkt A", 1, 1, Instant.now())
        ));

        List<DeviceSummaryRow> rows = service.getDeviceRows("device-2");

        assertEquals(1, rows.size());
        assertEquals("device-2", rows.getFirst().deviceId());
        assertEquals("Telefon 2", rows.getFirst().deviceName());
        assertEquals("111", rows.getFirst().barcode());
    }

    @Test
    void usesLatestDeviceNameForAllRowsOfSameDevice() {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        DeliveryService service = new DeliveryService(repository);
        DeliveryRecord activeDelivery = new DeliveryRecord(
                "delivery-1",
                "source.pdf",
                "ACTIVE",
                Instant.now(),
                Instant.now(),
                List.of(
                        new DeliveryItem("delivery-1", "111", "Produkt A", 5),
                        new DeliveryItem("delivery-1", "222", "Produkt B", 2)
                )
        );
        Instant older = Instant.parse("2026-04-22T08:00:00Z");
        Instant newer = Instant.parse("2026-04-22T08:05:00Z");

        when(repository.findActive()).thenReturn(Optional.of(activeDelivery));
        when(repository.findScans("delivery-1")).thenReturn(List.of(
                new DeviceScanState("device-1", "Android Device", "111", "Produkt A", 3, 1, older),
                new DeviceScanState("device-1", "Android Device1", "222", "Produkt B", 1, 2, newer)
        ));

        List<DeviceSummaryRow> rows = service.getDeviceRows();

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.deviceId().equals("device-1")));
        assertTrue(rows.stream().allMatch(row -> row.deviceName().equals("Android Device1")));
    }

    private ScanUpdateRequest request(String deliveryId, String deviceId, String barcode, int quantity, long revision, Instant updatedAt) {
        ScanUpdateRequest request = new ScanUpdateRequest();
        request.setDeliveryId(deliveryId);
        request.setDeviceId(deviceId);
        request.setDeviceName("Telefon 1");
        request.setBarcode(barcode);
        request.setName("Produkt A");
        request.setQuantity(quantity);
        request.setFromDelivery(true);
        request.setRevision(revision);
        request.setUpdatedAt(updatedAt);
        return request;
    }
}
