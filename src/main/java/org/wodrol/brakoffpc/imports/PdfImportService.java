package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfImportService {

    private static final int MIN_BARCODE_LENGTH = 8;
    private static final Pattern STRICT_LINE_PATTERN = Pattern.compile("^(?:\\d+\\s+)?(?<barcode>\\d{" + MIN_BARCODE_LENGTH + ",14})\\s+(?<name>.+?)\\s+(?<qty>\\d+)\\s*$");
    private static final Pattern TRAILING_PRICE_PATTERN = Pattern.compile("(?iu)(?:[-|\\u2013\\u2014]+\\s*)?\\d+(?:[,.]\\d{1,2})?\\s*(?:pln|zl|z\\u0142)\\.?\\s*[-|\\u2013\\u2014]*\\s*$");
    private static final Pattern TRAILING_OCR_SEPARATOR_PATTERN = Pattern.compile("[\\s\\-_|\\u2013\\u2014]+$");
    private static final List<String> HEADER_MARKERS = List.of("barcode", "kod", "ean", "nazwa", "ilosc", "qty", "quantity");
    private static final List<String> FOOTER_MARKERS = List.of("razem", "suma", "wartosc", "podsumowanie", "signature", "podpis");
    private final PdfOcrService pdfOcrService;

    public PdfImportService(PdfOcrService pdfOcrService) {
        this.pdfOcrService = pdfOcrService;
    }

    public List<ImportDraftItem> extractItems(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = extractTextLayer(document);
            List<ImportDraftItem> items = parseLines(text);
            if (!items.isEmpty() && !shouldFallbackToOcr(text, items)) {
                return items;
            }

            try {
                String ocrText = pdfOcrService.extractText(document);
                List<ImportDraftItem> ocrItems = parseLines(ocrText);
                if (!ocrItems.isEmpty()) {
                    return ocrItems;
                }
            } catch (PdfImportException exception) {
                if (requiresOcrResult(text, items)) {
                    throw exception;
                }
            }

            if (!items.isEmpty() && !textLayerLooksCorrupted(text)) {
                return items;
            }
            throw new PdfImportException("Nie znaleziono tabeli dostawy w pliku PDF.");
        } catch (IOException exception) {
            throw new PdfImportException("Nie udalo sie odczytac danych z pliku PDF.", exception);
        }
    }

    List<ImportDraftItem> parseLines(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(this::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .toList();
        List<String> candidateLines = selectCandidateLines(lines);
        List<ImportDraftItem> items = new ArrayList<>();

        int rowOrder = 0;
        for (String line : candidateLines) {
            if (isDocumentMetadataLine(line)) {
                continue;
            }
            ParsedLine parsedLine = parseCandidateLine(line);
            if (parsedLine != null) {
                items.add(new ImportDraftItem(rowOrder++, parsedLine.barcode(), parsedLine.name(), parsedLine.expectedQty()));
            }
        }
        return items;
    }

    private List<String> selectCandidateLines(List<String> lines) {
        int headerIndex = findHeaderIndex(lines);
        if (headerIndex < 0) {
            return lines;
        }

        List<String> section = new ArrayList<>();
        for (int index = headerIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalized = line.toLowerCase(Locale.ROOT);
            if (FOOTER_MARKERS.stream().anyMatch(normalized::contains)) {
                break;
            }
            section.add(line);
        }
        return section;
    }

    private int findHeaderIndex(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String normalized = lines.get(index).toLowerCase(Locale.ROOT);
            long matchedMarkers = HEADER_MARKERS.stream().filter(normalized::contains).count();
            if (matchedMarkers >= 2) {
                return index;
            }
        }
        return -1;
    }

    private ParsedLine parseCandidateLine(String line) {
        Matcher strictMatcher = STRICT_LINE_PATTERN.matcher(line);
        if (strictMatcher.matches()) {
            String normalizedName = normalizeItemName(stripTrailingPrice(strictMatcher.group("name")));
            if (normalizedName == null) {
                return null;
            }
            return new ParsedLine(
                    normalizeBarcodeToken(strictMatcher.group("barcode")),
                    normalizedName,
                    Integer.parseInt(strictMatcher.group("qty"))
            );
        }

        String[] tokens = line.split(" ");
        if (tokens.length < 3) {
            return null;
        }

        int qtyIndex = findLastQuantityIndex(tokens);
        if (qtyIndex <= 0) {
            return null;
        }

        int barcodeIndex = findBarcodeIndex(tokens, qtyIndex);
        if (barcodeIndex < 0 || barcodeIndex >= qtyIndex) {
            return null;
        }

        String barcode = normalizeBarcodeToken(tokens[barcodeIndex]);
        String name = normalizeItemName(stripTrailingPrice(String.join(" ", Arrays.copyOfRange(tokens, barcodeIndex + 1, qtyIndex))));
        if (name == null) {
            return null;
        }

        String quantity = extractDigits(tokens[qtyIndex]);
        if (quantity == null) {
            return null;
        }

        return new ParsedLine(barcode, name, Integer.parseInt(quantity));
    }

    private int findLastQuantityIndex(String[] tokens) {
        for (int index = tokens.length - 1; index >= 0; index--) {
            if (extractDigits(tokens[index]) != null) {
                return index;
            }
        }
        return -1;
    }

    private int findBarcodeIndex(String[] tokens, int qtyIndex) {
        for (int index = 0; index < qtyIndex; index++) {
            String barcode = normalizeBarcodeToken(tokens[index]);
            if (barcode != null && barcode.length() >= MIN_BARCODE_LENGTH) {
                return index;
            }
        }
        return -1;
    }

    private String normalizeBarcodeToken(String token) {
        String digits = extractDigits(token);
        if (digits == null) {
            return null;
        }
        if (digits.length() > 13) {
            return digits.substring(digits.length() - 13);
        }
        return digits;
    }

    private String extractDigits(String token) {
        String digits = token.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private String normalizeItemName(String rawName) {
        if (rawName == null) {
            return null;
        }

        String trimmed = rawName.trim();
        int firstContentIndex = 0;
        while (firstContentIndex < trimmed.length()) {
            char character = trimmed.charAt(firstContentIndex);
            if (Character.isLetterOrDigit(character)) {
                break;
            }
            firstContentIndex++;
        }

        if (firstContentIndex >= trimmed.length()) {
            return null;
        }

        String normalized = trimmed.substring(firstContentIndex).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String stripTrailingPrice(String rawName) {
        if (rawName == null) {
            return null;
        }

        String normalized = TRAILING_PRICE_PATTERN.matcher(rawName.trim()).replaceFirst("");
        normalized = TRAILING_OCR_SEPARATOR_PATTERN.matcher(normalized).replaceFirst("");
        return normalized.trim();
    }

    private boolean isDocumentMetadataLine(String line) {
        String normalized = normalizeForMatching(line);
        return normalized.contains("przyjecie magazynowe")
                || normalized.contains("dokument handlowy")
                || normalized.contains("data i miejsce")
                || normalized.contains("magazyn:")
                || normalized.contains("dostawca")
                || normalized.contains("kontrahent")
                || normalized.contains("strona:")
                || normalized.startsWith("lp.")
                || normalized.startsWith("suma:")
                || normalized.contains("kod towaru")
                || normalized.contains("nazwa towaru")
                || normalized.contains("dokument wystawil")
                || normalized.contains("podpis osoby")
                || normalized.contains("soneta");
    }

    private boolean shouldFallbackToOcr(String text, List<ImportDraftItem> items) {
        return looksLikeScannedPdf(text)
                || textLayerLooksCorrupted(text)
                || parsedItemsLookUnreliable(items);
    }

    private boolean requiresOcrResult(String text, List<ImportDraftItem> items) {
        return looksLikeScannedPdf(text)
                || textLayerLooksCorrupted(text)
                || (!items.isEmpty() && parsedItemsLookUnreliable(items));
    }

    private boolean parsedItemsLookUnreliable(List<ImportDraftItem> items) {
        if (items.isEmpty()) {
            return true;
        }

        long shortBarcodes = items.stream()
                .map(ImportDraftItem::barcode)
                .filter(barcode -> barcode == null || barcode.length() < MIN_BARCODE_LENGTH)
                .count();
        return shortBarcodes > 0;
    }

    private boolean textLayerLooksCorrupted(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        long visibleCharacters = text.chars().filter(character -> !Character.isWhitespace(character)).count();
        if (visibleCharacters < 100) {
            return false;
        }

        long letters = text.chars().filter(Character::isLetter).count();
        long controlCharacters = text.chars()
                .filter(character -> Character.isISOControl(character) && !Character.isWhitespace(character))
                .count();

        return letters * 100 < visibleCharacters * 12
                || controlCharacters * 20 > visibleCharacters;
    }

    private boolean looksLikeScannedPdf(String text) {
        if (text == null) {
            return true;
        }
        long visibleCharacters = text.chars().filter(character -> !Character.isWhitespace(character)).count();
        return visibleCharacters < 10;
    }

    private String extractTextLayer(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(document);
    }

    private String normalizeWhitespace(String line) {
        return line.replace('\u00A0', ' ').replaceAll("\\s{2,}", " ").trim();
    }

    private String normalizeForMatching(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private record ParsedLine(String barcode, String name, Integer expectedQty) {
    }
}
