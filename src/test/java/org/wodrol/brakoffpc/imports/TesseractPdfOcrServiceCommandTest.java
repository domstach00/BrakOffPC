package org.wodrol.brakoffpc.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesseractPdfOcrServiceCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseBundledWindowsTesseractWhenDefaultCommandIsConfigured() throws IOException {
        Path appDirectory = tempDir.resolve("BrakOffPC");
        Path launcherPath = appDirectory.resolve("BrakOffPC.exe");
        Path tesseractDirectory = appDirectory.resolve("tesseract");
        Path tesseractPath = tesseractDirectory.resolve("tesseract.exe");
        Path tessdataDirectory = tesseractDirectory.resolve("tessdata");
        Files.createDirectories(tessdataDirectory);
        Files.writeString(launcherPath, "");
        Files.writeString(tesseractPath, "");

        TesseractPdfOcrService service = new TesseractPdfOcrService(
                "pdftoppm",
                "tesseract",
                "pol+eng",
                6,
                400,
                "Windows 11",
                () -> Optional.of(launcherPath)
        );

        List<String> command = service.tesseractCommand(tempDir.resolve("page-1.png"));

        assertEquals(tesseractPath.toString(), command.getFirst());
        assertTrue(command.contains("--tessdata-dir"));
        assertTrue(command.contains(tessdataDirectory.toString()));
    }

    @Test
    void shouldKeepExplicitTesseractCommand() {
        TesseractPdfOcrService service = new TesseractPdfOcrService(
                "pdftoppm",
                "C:\\OCR\\tesseract.exe",
                "pol+eng",
                6,
                400,
                "Windows 11",
                Optional::<Path>empty
        );

        List<String> command = service.tesseractCommand(tempDir.resolve("page-1.png"));

        assertEquals("C:\\OCR\\tesseract.exe", command.getFirst());
        assertFalse(command.contains("--tessdata-dir"));
    }
}
