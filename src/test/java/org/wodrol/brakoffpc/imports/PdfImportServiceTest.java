package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfImportServiceTest {

    private static final String OCR_TEXT = """
            O Wi 4 YOESP-20.0. 5Pk Przyjecie magazynowe PZ/04/2026/000101
            55-093 Kielczow Data i miejsce wystawienia: 17.04.2026, Kielczow
            NIP: 895-00-10-661 Data operacji: 17.04.2026
            Dokument handlowy: ZK/04/2026/000091 z dnia: 17.04.2026
            Magazyn: Firma
            Kontrahent :
            MAJSTER BUDOWLANE ABC SPOLKA Z OGRANICZONA ODPOWIEDZIALNOSCIA
            ul. Duninowska 9
            87-800 Wloclawek
            NIP: 8883093173
            Dostawca :
            RAVI SPOLKA Z OGRANICZONA ODPOWIEDZIALNOSCIA
            ul. Galicyjska 7
            39-207 Brzeznica
            NIP: 8722149075
            Lp. Kod towaru Nazwa towaru Cena Detaliczna Ilosc jm.
            1 5906900702639 KUBKI DO PIWA 500ML wielokrotnego uzytku 6szt 4,00 PLN 10 szt
            - 2 5906900203440 NJB SLOMKI PAPIEROWE 12SZT DOTS 2,50 PLN 20 szt
            3 5906900015678 NJ PATYCZKI DO SZASZLYKOW 25CM 50SZT 2,50 PLN 20 szt
            4 5906900718326 NJ WYKALACZKI BAMBUSOWE EXTRA 320SZT 400PLN 30 szt
            5 5906900303096 NJ KUBKI PAPIEROWE BIO 250ML BIALE 6SZT 400PLN 10 szt
            6 5906900813298 NJ WIDELCE WIELOKRTONEGO UZYTKU 6SZT. 250PLN 30 szt
            7 5906900813281 NJ NOZ WIELOKROTNEGO UZYTKU 6SZT. 250PLN 30 szt
            8 5906900818231 NJB NOZE DREWNIANE 6SZT 2,50 PLN 30 szt
            9 5906900818224 NJB WIDELCE DREWNIANE 6SZT 3,00 PLN 30 szt
            10 5906900103924 NJ TALERZE PAPIEROWE 23CM 12SZT 5,90PLN 20 szt
            11 5906900003071 TALERZYKI PAPIEROWE 18CM -12SZT 4,90PLN 25 szt
            12 5906900001060 TACKI ALU DO GRILLA 34X22,5X2,5CM 4SZT 7,50 PLN 15 szt
            13 5906900001138 NJ TACKI OKRAGLE DO GRILLA FI34CM 2SZT. 7,90 PLN 15 szt
            14 5906900013131 KUBKI DO ZIMNYCH NAPOJOW 200ML , 12SZT 2,50 PLN 10 szt
            15 5906900013148 KUBKI DO CIEPLYCH NAPOJOW 200ML, 12SZT 3,00PLN 10 sz
            16 5906900313507 KUBKI DO NAPOJOW ZIMNYCH RAINBOW 200ML , 6SZT 3,00 PLN 10 szt
            * 17 5906900003064 TACKI PAPIEROWE 13,5X20,5CM 12SZT 3,00PLN 25 szt
            Podsumowanie ilosci: 340 szt
            """;

    private static final String GRAMAR_OCR_TEXT = """
            N ey J P.z00.5P  Przyjecie magazynowe PZ/05/2026/000153
            Lp. Kod towaru Nazwa towaru Cena Detaliczna Ilosc jm.
            1 5901448340046 PCHACZ MOTYL 19,00 PLN 1 szt
            2 5901448340244 PCHACZ KACZOR Z DZWONKIEM 19,90 PLN 1 szt
            43 5907803717102 TOREBKA OZDOBNA PAW T-2L 10-SZT 22.5 X 31.5 X 11 CM 7,00 PLN 10 szt
            WODROL Przyjecie magazynowe PZ/05/2026/000153 strona: 2
            Lp. Kod towaru Nazwa towaru Cena Detaliczna Ilosc jm.
            46 5904073168481 DELFIN ZLOTE KROPKI 28 CM 20,00 PLN 4 szt
            84 5902414008908 MODELINA INSPIRIA 6 KOLOROW 17,00 PLN 1 kpl
            Suma:
            Podsumowanie ilosci: 467 szt, 7 kpl
            """;

    private final PdfImportService pdfImportService = new PdfImportService(document -> "");

    @Test
    void parsesRowsFromDetectedTableSection() {
        String text = """
                Dokument dostawy 12/04/2026
                Kod Nazwa Ilosc
                1 000123456789 Produkt testowy A 12
                2 998877665544 Inny-produkt 3
                Razem 2
                """;

        List<ImportDraftItem> items = pdfImportService.parseLines(text);

        assertEquals(2, items.size());
        assertEquals("000123456789", items.get(0).barcode());
        assertEquals("Produkt testowy A", items.get(0).name());
        assertEquals(12, items.get(0).expectedQty());
        assertEquals("szt", items.get(0).unit());
        assertEquals("998877665544", items.get(1).barcode());
    }

    @Test
    void fallsBackToHeuristicParsingWithoutHeader() {
        String text = """
                123456789012 Przewod USB-C 4
                00000055 Adapter HDMI mini 1
                """;

        List<ImportDraftItem> items = pdfImportService.parseLines(text);

        assertEquals(2, items.size());
        assertEquals("123456789012", items.get(0).barcode());
        assertEquals("Przewod USB-C", items.get(0).name());
        assertEquals(4, items.get(0).expectedQty());
        assertEquals("szt", items.get(0).unit());
        assertEquals("00000055", items.get(1).barcode());
    }

    @Test
    void parsesRowsFromOcrLikeTableLayout() {
        List<ImportDraftItem> items = pdfImportService.parseLines(OCR_TEXT);

        assertEquals(17, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("KUBKI DO PIWA 500ML wielokrotnego uzytku 6szt", items.getFirst().name());
        assertEquals(10, items.getFirst().expectedQty());
        assertEquals("szt", items.getFirst().unit());
        assertEquals("5906900003064", items.getLast().barcode());
        assertEquals("TACKI PAPIEROWE 13,5X20,5CM 12SZT", items.getLast().name());
        assertEquals(25, items.getLast().expectedQty());
        assertEquals("szt", items.getLast().unit());
    }

    @Test
    void parsesRowsFromMicrosoftPrintToPdfOcrLayout() {
        List<ImportDraftItem> items = pdfImportService.parseLines(GRAMAR_OCR_TEXT);

        assertEquals(5, items.size());
        assertEquals("5901448340046", items.getFirst().barcode());
        assertEquals("PCHACZ MOTYL", items.getFirst().name());
        assertEquals(1, items.getFirst().expectedQty());
        assertEquals("szt", items.getFirst().unit());
        assertEquals("5907803717102", items.get(2).barcode());
        assertEquals("TOREBKA OZDOBNA PAW T-2L 10-SZT 22.5 X 31.5 X 11 CM", items.get(2).name());
        assertEquals(10, items.get(2).expectedQty());
        assertEquals("5902414008908", items.getLast().barcode());
        assertEquals("MODELINA INSPIRIA 6 KOLOROW", items.getLast().name());
        assertEquals(1, items.getLast().expectedQty());
        assertEquals("kpl", items.getLast().unit());
    }

    @Test
    void extractsDeliveryMetadataFromHermesLikeOcrText() {
        String text = """
                WODROL Przyjecie magazynowe PZ/05/2026/000160
                Data i miejsce wystawienia: 20.05.2026, Kielczow
                Dokument handlowy: ZK/05/2026/000159 z dnia: 20.05.2026
                Magazyn: Firma
                Dostawca :
                HERMES ZALECH SPÓŁKA JAWNA
                ul. Testowa 1
                NIP: 1234567890
                Lp. Kod towaru Nazwa towaru Cena Detaliczna Ilosc jm.
                1 5900000000001 TEST PRODUKT 4,00 PLN 10 szt
                """;

        assertEquals("HERMES ZALECH SPÓŁKA JAWNA", pdfImportService.extractSupplierName(text));
        assertEquals("ZK/05/2026/000159", pdfImportService.extractCommercialDocumentNumber(text));
        assertEquals("PZ/05/2026/000160", pdfImportService.extractWarehouseDocumentNumber(text));
    }

    @Test
    void parsesArbitraryMeasurementUnits() {
        String text = """
                Kod Nazwa Ilosc jm.
                1 5900000000001 REKAWICE ROBOCZE 5,00 PLN 5 para
                2 5900000000002 LISTWA PRZYPODLOGOWA 12,00 PLN 10 mb
                Razem 2
                """;

        List<ImportDraftItem> items = pdfImportService.parseLines(text);

        assertEquals(2, items.size());
        assertEquals("para", items.get(0).unit());
        assertEquals(5, items.get(0).expectedQty());
        assertEquals("mb", items.get(1).unit());
        assertEquals(10, items.get(1).expectedQty());
    }

    @Test
    void ignoresTrailingPriceColumnsAfterQuantityAndUnit() {
        String text = """
                Kod Nazwa Ilosc jm.
                1 5900000000001 REKAWICE ROBOCZE 5 para 12,50 PLN
                2 5900000000002 LISTWA PRZYPODLOGOWA 10 19.99
                Razem 2
                """;

        List<ImportDraftItem> items = pdfImportService.parseLines(text);

        assertEquals(2, items.size());
        assertEquals(5, items.get(0).expectedQty());
        assertEquals("para", items.get(0).unit());
        assertEquals(10, items.get(1).expectedQty());
        assertEquals("szt", items.get(1).unit());
    }

    @Test
    void removesLeadingSpecialCharactersFromParsedName() {
        String text = """
                Kod Nazwa Ilosc
                1 5906900203440 " " NJB SLOMKI PAPIEROWE 12SZT DOTS 20
                2 5906900818224 — — — NJB WIDELCE DREWNIANE 6SZT 30
                Razem 2
                """;

        List<ImportDraftItem> items = pdfImportService.parseLines(text);

        assertEquals(2, items.size());
        assertEquals("NJB SLOMKI PAPIEROWE 12SZT DOTS", items.get(0).name());
        assertEquals("NJB WIDELCE DREWNIANE 6SZT", items.get(1).name());
    }

    @Test
    void fallsBackToOcrWhenTextLayerLooksCorruptedEvenIfItParsesRows() throws IOException {
        AtomicBoolean ocrCalled = new AtomicBoolean(false);
        PdfImportService service = new PdfImportService(document -> {
            ocrCalled.set(true);
            return GRAMAR_OCR_TEXT;
        });
        MockMultipartFile file = new MockMultipartFile(
                "pdfFile",
                "print-to-pdf.pdf",
                "application/pdf",
                TestPdfFactory.createTextPdf(List.of(
                        "1234567890123 111111111111111111111111111111111111111111111111111111111111111111111 2",
                        "1234567890124 222222222222222222222222222222222222222222222222222222222222222222222 4"
                ))
        );

        List<ImportDraftItem> items = service.extractItems(file);

        assertTrue(ocrCalled.get());
        assertEquals(5, items.size());
        assertEquals("5901448340046", items.getFirst().barcode());
        assertEquals("PCHACZ MOTYL", items.getFirst().name());
    }

    @Test
    void fallsBackToOcrWhenPdfHasNoTextLayer() throws IOException {
        PdfImportService service = new PdfImportService(document -> OCR_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "pdfFile",
                "scan.pdf",
                "application/pdf",
                TestPdfFactory.createImageOnlyPdf(List.of(
                        "5906900702639 KUBKI DO PIWA 500ML 10",
                        "5906900203440 SLOMKI PAPIEROWE 20"
                ))
        );

        List<ImportDraftItem> items = service.extractItems(file);

        assertEquals(17, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("5906900003064", items.getLast().barcode());
    }
}
