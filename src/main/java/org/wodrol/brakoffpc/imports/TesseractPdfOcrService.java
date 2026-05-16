package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wodrol.brakoffpc.config.AppPaths;
import org.wodrol.brakoffpc.desktop.DesktopLauncherSupport;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Service
public class TesseractPdfOcrService implements PdfOcrService {

    private static final String DEFAULT_PDFTOPPM_COMMAND = "pdftoppm";
    private static final String WINDOWS_PDFTOPPM_EXECUTABLE = "pdftoppm.exe";
    private static final String DEFAULT_TESSERACT_COMMAND = "tesseract";
    private static final String WINDOWS_TESSERACT_EXECUTABLE = "tesseract.exe";
    private final String pdftoppmCommand;
    private final String tesseractCommand;
    private final String language;
    private final int pageSegmentationMode;
    private final float dpi;
    private final String osName;
    private final Supplier<Optional<Path>> launcherPathSupplier;

    @Autowired
    public TesseractPdfOcrService(
            @Value("${app.ocr.pdftoppm-command:pdftoppm}") String pdftoppmCommand,
            @Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${app.ocr.language:pol+eng}") String language,
            @Value("${app.ocr.page-segmentation-mode:6}") int pageSegmentationMode,
            @Value("${app.ocr.dpi:400}") float dpi
    ) {
        this(
                pdftoppmCommand,
                tesseractCommand,
                language,
                pageSegmentationMode,
                dpi,
                System.getProperty("os.name"),
                DesktopLauncherSupport::detectLauncherPath
        );
    }

    TesseractPdfOcrService(
            String pdftoppmCommand,
            String tesseractCommand,
            String language,
            int pageSegmentationMode,
            float dpi,
            String osName,
            Supplier<Optional<Path>> launcherPathSupplier
    ) {
        this.pdftoppmCommand = pdftoppmCommand;
        this.tesseractCommand = tesseractCommand;
        this.language = language;
        this.pageSegmentationMode = pageSegmentationMode;
        this.dpi = dpi;
        this.osName = osName;
        this.launcherPathSupplier = launcherPathSupplier;
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
            process = new ProcessBuilder(pdftoppmCommand(pdfPath, tempDirectory.resolve("page")))
                    .redirectErrorStream(true)
                    .start();
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

    List<String> pdftoppmCommand(Path pdfPath, Path outputPrefix) {
        return List.of(
                resolvePdftoppmExecutable(),
                "-r",
                String.valueOf(Math.round(dpi)),
                "-png",
                pdfPath.toString(),
                outputPrefix.toString()
        );
    }

    private String runTesseract(Path imagePath) throws IOException {
        Process process;
        try {
            TesseractExecutable executable = resolveTesseractExecutable();
            ProcessBuilder processBuilder = new ProcessBuilder(tesseractCommand(imagePath, executable));
            processBuilder.redirectErrorStream(true);
            executable.tessdataDirectory()
                    .ifPresent(tessdataDirectory -> processBuilder.environment().put("TESSDATA_PREFIX", tessdataDirectory.toString()));
            process = processBuilder.start();
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

    List<String> tesseractCommand(Path imagePath) {
        return tesseractCommand(imagePath, resolveTesseractExecutable());
    }

    private List<String> tesseractCommand(Path imagePath, TesseractExecutable executable) {
        List<String> command = new ArrayList<>(List.of(
                executable.command(),
                imagePath.toString(),
                "stdout",
                "-l",
                language,
                "--psm",
                String.valueOf(pageSegmentationMode)
        ));

        executable.tessdataDirectory().ifPresent(tessdataDirectory -> {
            command.add("--tessdata-dir");
            command.add(tessdataDirectory.toString());
        });

        return command;
    }

    private String resolvePdftoppmExecutable() {
        if (!usesDefaultPdftoppmCommand() || !AppPaths.isWindows(osName)) {
            return pdftoppmCommand;
        }

        return detectBundledPdftoppm()
                .map(Path::toString)
                .orElse(pdftoppmCommand);
    }

    private Optional<Path> detectBundledPdftoppm() {
        return launcherPathSupplier.get()
                .map(Path::getParent)
                .flatMap(this::findBundledPdftoppm);
    }

    private Optional<Path> findBundledPdftoppm(Path appDirectory) {
        for (Path candidatePath : bundledPdftoppmPaths(appDirectory)) {
            if (Files.isRegularFile(candidatePath)) {
                return Optional.of(candidatePath);
            }
        }

        return Optional.empty();
    }

    private List<Path> bundledPdftoppmPaths(Path appDirectory) {
        return List.of(
                appDirectory.resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("pdftoppm").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("poppler").resolve("bin").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("tools").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("tools").resolve("pdftoppm").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("tools").resolve("poppler").resolve("bin").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("app").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("app").resolve("pdftoppm").resolve(WINDOWS_PDFTOPPM_EXECUTABLE),
                appDirectory.resolve("app").resolve("poppler").resolve("bin").resolve(WINDOWS_PDFTOPPM_EXECUTABLE)
        );
    }

    private TesseractExecutable resolveTesseractExecutable() {
        if (!usesDefaultTesseractCommand() || !AppPaths.isWindows(osName)) {
            return new TesseractExecutable(tesseractCommand, Optional.empty());
        }

        return detectBundledTesseract()
                .orElseGet(() -> new TesseractExecutable(tesseractCommand, Optional.empty()));
    }

    private Optional<TesseractExecutable> detectBundledTesseract() {
        return launcherPathSupplier.get()
                .map(Path::getParent)
                .flatMap(this::findBundledTesseract);
    }

    private Optional<TesseractExecutable> findBundledTesseract(Path appDirectory) {
        for (Path candidateDirectory : bundledTesseractDirectories(appDirectory)) {
            Path executablePath = candidateDirectory.resolve(WINDOWS_TESSERACT_EXECUTABLE);
            if (Files.isRegularFile(executablePath)) {
                Path tessdataDirectory = candidateDirectory.resolve("tessdata");
                return Optional.of(new TesseractExecutable(
                        executablePath.toString(),
                        Files.isDirectory(tessdataDirectory) ? Optional.of(tessdataDirectory) : Optional.empty()
                ));
            }
        }

        return Optional.empty();
    }

    private List<Path> bundledTesseractDirectories(Path appDirectory) {
        return List.of(
                appDirectory.resolve("tesseract"),
                appDirectory.resolve("tools").resolve("tesseract"),
                appDirectory.resolve("app").resolve("tesseract")
        );
    }

    private boolean usesDefaultPdftoppmCommand() {
        if (pdftoppmCommand == null || pdftoppmCommand.isBlank()) {
            return true;
        }

        String command = pdftoppmCommand.trim();
        return DEFAULT_PDFTOPPM_COMMAND.equalsIgnoreCase(command)
                || WINDOWS_PDFTOPPM_EXECUTABLE.equalsIgnoreCase(command);
    }

    private boolean usesDefaultTesseractCommand() {
        if (tesseractCommand == null || tesseractCommand.isBlank()) {
            return true;
        }

        String command = tesseractCommand.trim();
        return DEFAULT_TESSERACT_COMMAND.equalsIgnoreCase(command)
                || WINDOWS_TESSERACT_EXECUTABLE.equalsIgnoreCase(command);
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

    private record TesseractExecutable(String command, Optional<Path> tessdataDirectory) {
    }
}
