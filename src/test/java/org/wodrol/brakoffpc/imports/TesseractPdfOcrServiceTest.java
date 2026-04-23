package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TesseractPdfOcrServiceTest {

    @Test
    void extractsItemsFromSampleScannedPdf() throws IOException, InterruptedException {
        Assumptions.assumeTrue(isTesseractAvailable());

        TesseractPdfOcrService ocrService = new TesseractPdfOcrService("pdftoppm", "tesseract", "pol+eng", 6, 400);
        PdfImportService service = new PdfImportService(ocrService);
        MockMultipartFile file = new MockMultipartFile(
                "pdfFile",
                "scan.pdf",
                "application/pdf",
                TestPdfFactory.createImageOnlyPdf(List.of("5906900702639 TESTOWY PRODUKT OCR 10"))
        );

        List<ImportDraftItem> items = service.extractItems(file);

        assertEquals(1, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("TESTOWY PRODUKT OCR", items.getFirst().name());
        assertEquals(10, items.getFirst().expectedQty());
    }

    private boolean isTesseractAvailable() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("tesseract", "--version").start();
        process.getInputStream().readAllBytes();
        process.getErrorStream().readAllBytes();
        return process.waitFor() == 0;
    }
}
