package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("00000055", items.get(1).barcode());
    }

    @Test
    void parsesRowsFromOcrLikeTableLayout() {
        List<ImportDraftItem> items = pdfImportService.parseLines(OCR_TEXT);

        assertEquals(17, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("KUBKI DO PIWA 500ML wielokrotnego uzytku 6szt 4,00 PLN", items.getFirst().name());
        assertEquals(10, items.getFirst().expectedQty());
        assertEquals("5906900003064", items.getLast().barcode());
        assertEquals(25, items.getLast().expectedQty());
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
    void fallsBackToOcrWhenPdfHasNoTextLayer() throws IOException {
        PdfImportService service = new PdfImportService(document -> OCR_TEXT);
        MockMultipartFile file = new MockMultipartFile(
                "pdfFile",
                "CCF_000470.pdf",
                "application/pdf",
                Files.readAllBytes(Path.of("CCF_000470.pdf"))
        );

        List<ImportDraftItem> items = service.extractItems(file);

        assertEquals(17, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("5906900003064", items.getLast().barcode());
    }
}
