package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                "CCF_000470.pdf",
                "application/pdf",
                Files.readAllBytes(Path.of("CCF_000470.pdf"))
        );

        List<ImportDraftItem> items = service.extractItems(file);

        assertEquals(17, items.size());
        assertEquals("5906900702639", items.getFirst().barcode());
        assertEquals("5906900003064", items.getLast().barcode());
    }

    private boolean isTesseractAvailable() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("tesseract", "--version").start();
        process.getInputStream().readAllBytes();
        process.getErrorStream().readAllBytes();
        return process.waitFor() == 0;
    }
}
