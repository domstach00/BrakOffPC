package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class TesseractPdfOcrService implements PdfOcrService {

    private final String pdftoppmCommand;
    private final String tesseractCommand;
    private final String language;
    private final int pageSegmentationMode;
    private final float dpi;

    public TesseractPdfOcrService(
            @Value("${app.ocr.pdftoppm-command:pdftoppm}") String pdftoppmCommand,
            @Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${app.ocr.language:pol+eng}") String language,
            @Value("${app.ocr.page-segmentation-mode:6}") int pageSegmentationMode,
            @Value("${app.ocr.dpi:400}") float dpi
    ) {
        this.pdftoppmCommand = pdftoppmCommand;
        this.tesseractCommand = tesseractCommand;
        this.language = language;
        this.pageSegmentationMode = pageSegmentationMode;
        this.dpi = dpi;
    }

    @Override
    public String extractText(PDDocument document) {
        Path tempDirectory = null;
        try {
            System.setProperty("java.awt.headless", "true");
            tempDirectory = Files.createTempDirectory("brakoffpc-ocr-");
            StringBuilder text = new StringBuilder();
            List<Path> imagePaths = renderPages(document, tempDirectory);

            for (Path imagePath : imagePaths) {
                text.append(runTesseract(imagePath));
                text.append(System.lineSeparator());
            }

            return text.toString();
        } catch (IOException exception) {
            throw new PdfImportException("Nie udalo sie wykonac OCR dla pliku PDF.", exception);
        } finally {
            deleteQuietly(tempDirectory);
        }
    }

    private List<Path> renderPages(PDDocument document, Path tempDirectory) throws IOException {
        List<Path> imagePaths = tryRenderWithPdftoppm(document, tempDirectory);
        if (!imagePaths.isEmpty()) {
            return imagePaths;
        }

        PDFRenderer renderer = new PDFRenderer(document);
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.GRAY);
            Path imagePath = tempDirectory.resolve("page-" + (pageIndex + 1) + ".png");
            ImageIO.write(image, "png", imagePath.toFile());
        }
        return listRenderedPages(tempDirectory);
    }

    private List<Path> tryRenderWithPdftoppm(PDDocument document, Path tempDirectory) throws IOException {
        Path pdfPath = tempDirectory.resolve("source.pdf");
        document.save(pdfPath.toFile());

        Process process;
        try {
            process = new ProcessBuilder(
                    pdftoppmCommand,
                    "-r",
                    String.valueOf(Math.round(dpi)),
                    "-png",
                    pdfPath.toString(),
                    tempDirectory.resolve("page").toString()
            ).redirectErrorStream(true).start();
        } catch (IOException exception) {
            return List.of();
        }

        try {
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PdfImportException("Nie udalo sie przygotowac obrazu PDF do OCR. pdftoppm zakonczyl dzialanie kodem " + exitCode + ".");
            }
            return listRenderedPages(tempDirectory);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PdfImportException("Przygotowanie obrazu PDF do OCR zostalo przerwane.", exception);
        }
    }

    private List<Path> listRenderedPages(Path tempDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(tempDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith("page-"))
                    .filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted()
                    .toList();
        }
    }

    private String runTesseract(Path imagePath) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(
                    tesseractCommand,
                    imagePath.toString(),
                    "stdout",
                    "-l",
                    language,
                    "--psm",
                    String.valueOf(pageSegmentationMode)
            ).redirectErrorStream(true).start();
        } catch (IOException exception) {
            throw new PdfImportException("OCR wymaga lokalnie dostepnego polecenia 'tesseract' w systemowym PATH.", exception);
        }

        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PdfImportException("OCR nie powiodl sie. Tesseract zakonczyl dzialanie kodem " + exitCode + ".");
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PdfImportException("OCR zostal przerwany podczas przetwarzania PDF.", exception);
        }
    }

    private void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
