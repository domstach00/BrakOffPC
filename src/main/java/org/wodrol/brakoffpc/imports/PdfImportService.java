package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.wodrol.brakoffpc.common.MeasurementUnit;

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
    private static final Pattern STRICT_LINE_PATTERN = Pattern.compile("^(?:\\d+\\s+)?(?<barcode>\\d{" + MIN_BARCODE_LENGTH + ",14})\\s+(?<name>.+?)\\s+(?<qty>\\d+)(?:\\s+(?<unit>(?=[\\p{L}\\p{N}./-]*\\p{L})[\\p{L}\\p{N}./-]+))?\\s*$");
    private static final Pattern POSITION_SORTED_ROW_PATTERN = Pattern.compile("^(?<lp>\\d{1,4})\\s+(?<barcode>\\d{" + MIN_BARCODE_LENGTH + ",14})\\s+(?<tail>.*)$");
    private static final Pattern POSITION_SORTED_ROW_TAIL_PATTERN = Pattern.compile("^(?<name>.*?)(?:\\s+(?<price>\\d+(?:[,.]\\d{1,2})\\s*(?:pln|zl|z\\u0142)\\.?))?\\s+(?<qty>\\d+)\\s+(?<unit>(?=[\\p{L}\\p{N}./-]*\\p{L})[\\p{L}\\p{N}./-]+)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_PRICE_PATTERN = Pattern.compile("(?iu)(?:[-|\\u2013\\u2014]+\\s*)?\\d+(?:[,.]\\d{1,2})?\\s*(?:pln|zl|z\\u0142)\\.?\\s*[-|\\u2013\\u2014]*\\s*$");
    private static final Pattern TRAILING_PRICE_COLUMN_PATTERN = Pattern.compile("(?iu)(?:\\s+[-|\\u2013\\u2014]*\\s*)?(?:\\d+(?:[,.]\\d{1,2})\\s*(?:pln|zl|z\\u0142)\\.?|\\d+\\s*(?:pln|zl|z\\u0142)\\.?|\\d+[,.]\\d{1,2})\\s*[-|\\u2013\\u2014]*\\s*$");
    private static final Pattern TRAILING_OCR_SEPARATOR_PATTERN = Pattern.compile("[\\s\\-_|\\u2013\\u2014]+$");
    private static final Pattern COMMERCIAL_DOCUMENT_PATTERN = Pattern.compile("(?iu)dokument\\s+handlowy\\s*:\\s*(?<number>[A-Z]{1,6}/\\d{1,2}/\\d{4}/\\d+)");
    private static final Pattern WAREHOUSE_DOCUMENT_PATTERN = Pattern.compile("(?iu)przyj[eę]cie\\s+magazynowe\\s+(?<number>PZ/\\d{1,2}/\\d{4}/\\d+)");
    private static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile("(?iu)\\b(?<number>[A-Z]{1,6}/\\d{1,2}/\\d{4}/\\d+)\\b");
    private static final List<String> HEADER_MARKERS = List.of("barcode", "kod", "ean", "nazwa", "ilosc", "qty", "quantity");
    private static final List<String> FOOTER_MARKERS = List.of("razem", "suma", "wartosc", "podsumowanie", "signature", "podpis");
    private final PdfOcrService pdfOcrService;

    public PdfImportService(PdfOcrService pdfOcrService) {
        this.pdfOcrService = pdfOcrService;
    }

    public List<ImportDraftItem> extractItems(MultipartFile file) {
        return extractDeliveryData(file).items();
    }

    public PdfImportResult extractDeliveryData(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = extractTextLayer(document);
            List<ImportDraftItem> items = parseLines(text);
            PdfImportResult textLayerResult = buildResult(text, items);
            if (!items.isEmpty() && !shouldFallbackToOcr(text, items) && hasCompleteMetadata(textLayerResult)) {
                return textLayerResult;
            }

            try {
                String ocrText = pdfOcrService.extractText(document);
                List<ImportDraftItem> ocrItems = parseLines(ocrText);
                if (!ocrItems.isEmpty()) {
                    return buildResult(ocrText, ocrItems);
                }
            } catch (PdfImportException exception) {
                if (requiresOcrResult(text, items)) {
                    throw exception;
                }
            }

            if (!items.isEmpty() && !textLayerLooksCorrupted(text)) {
                return textLayerResult;
            }
            throw new PdfImportException("Nie znaleziono tabeli dostawy w pliku PDF.");
        } catch (IOException exception) {
            throw new PdfImportException("Nie udalo sie odczytac danych z pliku PDF.", exception);
        }
    }

    private PdfImportResult buildResult(String text, List<ImportDraftItem> items) {
        return new PdfImportResult(
                items,
                extractSupplierName(text),
                extractCommercialDocumentNumber(text),
                extractWarehouseDocumentNumber(text)
        );
    }

    private boolean hasCompleteMetadata(PdfImportResult result) {
        return result.supplierName() != null
                && result.commercialDocumentNumber() != null
                && result.warehouseDocumentNumber() != null;
    }

    List<ImportDraftItem> parseLines(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(this::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .toList();
        List<String> candidateLines = selectCandidateLines(lines);
        List<ImportDraftItem> positionSortedItems = parsePositionSortedRows(candidateLines);
        if (!positionSortedItems.isEmpty()) {
            return positionSortedItems;
        }
        List<ImportDraftItem> items = new ArrayList<>();

        int rowOrder = 0;
        for (String line : candidateLines) {
            if (isDocumentMetadataLine(line)) {
                continue;
            }
            ParsedLine parsedLine = parseCandidateLine(line);
            if (parsedLine != null) {
                items.add(new ImportDraftItem(rowOrder++, parsedLine.barcode(), parsedLine.name(), parsedLine.expectedQty(), parsedLine.unit()));
            }
        }
        return items;
    }

    private List<ImportDraftItem> parsePositionSortedRows(List<String> candidateLines) {
        List<PositionSortedBlock> blocks = new ArrayList<>();
        PositionSortedBlock currentBlock = null;
        boolean foundContinuationLine = false;

        for (int index = 0; index < candidateLines.size(); index++) {
            String line = candidateLines.get(index);
            PositionSortedBlockStart blockStart = parsePositionSortedBlockStart(line);
            if (blockStart != null) {
                List<IndexedFragment> movedPrefixes = currentBlock == null
                        ? List.of()
                        : extractTrailingPrefixesForNextBlock(currentBlock, blockStart.initialFragment());
                currentBlock = new PositionSortedBlock(blockStart.barcode(), new ArrayList<>());
                currentBlock.fragments().addAll(movedPrefixes);
                currentBlock.fragments().add(new IndexedFragment(index, blockStart.initialFragment()));
                blocks.add(currentBlock);
                continue;
            }
            if (currentBlock != null && isLikelyPositionSortedContinuationLine(line)) {
                currentBlock.fragments().add(new IndexedFragment(index, line));
                foundContinuationLine = true;
            }
        }

        if (blocks.size() < 2 || !foundContinuationLine) {
            return List.of();
        }

        List<ImportDraftItem> items = new ArrayList<>();
        for (PositionSortedBlock block : blocks) {
            ParsedLine parsedBlock = parsePositionSortedBlock(block);
            if (parsedBlock == null) {
                continue;
            }
            items.add(new ImportDraftItem(
                    items.size(),
                    parsedBlock.barcode(),
                    parsedBlock.name(),
                    parsedBlock.expectedQty(),
                    parsedBlock.unit()
            ));
        }
        return items;
    }

    private PositionSortedBlockStart parsePositionSortedBlockStart(String line) {
        Matcher rowMatcher = POSITION_SORTED_ROW_PATTERN.matcher(line);
        if (!rowMatcher.matches()) {
            return null;
        }

        String barcode = normalizeBarcodeToken(rowMatcher.group("barcode"));
        if (barcode == null) {
            return null;
        }
        return new PositionSortedBlockStart(barcode, rowMatcher.group("tail") == null ? "" : rowMatcher.group("tail").trim());
    }

    private boolean isLikelyPositionSortedContinuationLine(String line) {
        if (line == null || line.isBlank() || isDocumentMetadataLine(line)) {
            return false;
        }

        String normalized = normalizeForMatching(line);
        if (HEADER_MARKERS.stream().anyMatch(normalized::contains)
                || FOOTER_MARKERS.stream().anyMatch(normalized::contains)
                || normalized.startsWith("jm")
                || normalized.startsWith("cena detaliczna")
                || normalized.startsWith("ilosc")
                || normalized.startsWith("dokument wystawil")
                || normalized.contains("strona:")
                || normalized.contains("© soneta")) {
            return false;
        }

        if (POSITION_SORTED_ROW_PATTERN.matcher(line).matches()) {
            return false;
        }

        if (parseCandidateLine(line) != null) {
            return false;
        }

        String compact = extractLettersAndDigits(line);
        if (compact.isBlank()) {
            return false;
        }

        if (TRAILING_PRICE_COLUMN_PATTERN.matcher(line).matches()) {
            return false;
        }

        return line.chars().anyMatch(Character::isLetter);
    }

    private ParsedLine parsePositionSortedBlock(PositionSortedBlock block) {
        List<IndexedFragment> fragments = block.fragments().stream()
                .sorted((left, right) -> Integer.compare(left.lineIndex(), right.lineIndex()))
                .toList();

        int quantityFragmentIndex = -1;
        Integer expectedQty = null;
        String unit = null;
        String[] normalizedFragments = new String[fragments.size()];

        for (int index = fragments.size() - 1; index >= 0; index--) {
            String text = fragments.get(index).text();
            Matcher tailMatcher = POSITION_SORTED_ROW_TAIL_PATTERN.matcher(text);
            if (!tailMatcher.matches()) {
                normalizedFragments[index] = text;
                continue;
            }

            String normalizedUnit = MeasurementUnit.normalize(tailMatcher.group("unit"));
            String nameFragment = tailMatcher.group("name") == null ? "" : tailMatcher.group("name").trim();
            if (looksLikeDimensionSplit(nameFragment, normalizedUnit)) {
                normalizedFragments[index] = text;
                continue;
            }

            quantityFragmentIndex = index;
            expectedQty = parseExpectedQty(tailMatcher.group("qty"));
            unit = normalizedUnit;
            normalizedFragments[index] = nameFragment;
            break;
        }

        if (quantityFragmentIndex < 0) {
            return null;
        }

        for (int index = 0; index < fragments.size(); index++) {
            if (normalizedFragments[index] == null) {
                normalizedFragments[index] = fragments.get(index).text();
            }
        }

        String combinedName = Arrays.stream(normalizedFragments)
                .map(this::stripTrailingPrice)
                .map(String::trim)
                .filter(fragment -> !fragment.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        String normalizedName = normalizeItemName(combinedName);
        if (normalizedName == null) {
            return null;
        }

        return new ParsedLine(block.barcode(), normalizedName, expectedQty, unit == null ? MeasurementUnit.DEFAULT_UNIT : unit);
    }

    private List<IndexedFragment> extractTrailingPrefixesForNextBlock(PositionSortedBlock currentBlock, String nextInitialFragment) {
        if (!looksLikeIncompleteRowStart(nextInitialFragment)) {
            return List.of();
        }

        int quantityFragmentIndex = findLastQuantityFragmentIndex(currentBlock.fragments());
        if (quantityFragmentIndex < 0 || quantityFragmentIndex >= currentBlock.fragments().size() - 1) {
            return List.of();
        }

        List<IndexedFragment> moved = new ArrayList<>();
        for (int index = currentBlock.fragments().size() - 1; index > quantityFragmentIndex; index--) {
            IndexedFragment fragment = currentBlock.fragments().get(index);
            if (!looksLikeNextRowPrefixFragment(fragment.text())) {
                break;
            }
            moved.add(0, fragment);
            currentBlock.fragments().remove(index);
        }
        return moved;
    }

    private int findLastQuantityFragmentIndex(List<IndexedFragment> fragments) {
        for (int index = fragments.size() - 1; index >= 0; index--) {
            if (matchesPositionSortedTail(fragments.get(index).text())) {
                return index;
            }
        }
        return -1;
    }

    private boolean matchesPositionSortedTail(String text) {
        Matcher tailMatcher = POSITION_SORTED_ROW_TAIL_PATTERN.matcher(text);
        if (!tailMatcher.matches()) {
            return false;
        }
        String normalizedUnit = MeasurementUnit.normalize(tailMatcher.group("unit"));
        String nameFragment = tailMatcher.group("name") == null ? "" : tailMatcher.group("name").trim();
        return !looksLikeDimensionSplit(nameFragment, normalizedUnit);
    }

    private boolean looksLikeIncompleteRowStart(String fragment) {
        if (fragment == null) {
            return false;
        }
        String normalized = fragment.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.startsWith("+")) {
            return true;
        }
        return countWords(normalized) <= 2 && !matchesPositionSortedTail(normalized);
    }

    private boolean looksLikeNextRowPrefixFragment(String fragment) {
        if (fragment == null) {
            return false;
        }
        String normalized = fragment.trim();
        if (normalized.isEmpty() || matchesPositionSortedTail(normalized)) {
            return false;
        }
        if (normalized.contains("=")) {
            return false;
        }
        return countWords(normalized) >= 1 && normalized.chars().anyMatch(Character::isLetter);
    }

    private long countWords(String value) {
        return Arrays.stream(value.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
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
        String lineWithoutTrailingPriceColumns = stripTrailingPriceColumns(line);
        Matcher strictMatcher = STRICT_LINE_PATTERN.matcher(lineWithoutTrailingPriceColumns);
        ParsedLine overflowFallback = null;
        if (strictMatcher.matches()) {
            String normalizedName = normalizeItemName(stripTrailingPrice(strictMatcher.group("name")));
            if (normalizedName == null) {
                return null;
            }
            String normalizedUnit = MeasurementUnit.normalize(strictMatcher.group("unit"));
            if (looksLikeDimensionSplit(strictMatcher.group("name"), normalizedUnit)) {
                return null;
            }
            Integer expectedQty = parseExpectedQty(strictMatcher.group("qty"));
            ParsedLine strictParsedLine = new ParsedLine(
                    normalizeBarcodeToken(strictMatcher.group("barcode")),
                    normalizedName,
                    expectedQty,
                    normalizedUnit
            );
            if (expectedQty != null) {
                return strictParsedLine;
            }
            overflowFallback = strictParsedLine;
        }

        String[] tokens = lineWithoutTrailingPriceColumns.split(" ");
        if (tokens.length < 3) {
            return overflowFallback;
        }

        QuantityCandidate quantityCandidate = findLastQuantityCandidate(tokens);
        if (quantityCandidate == null || quantityCandidate.index() <= 0) {
            return overflowFallback;
        }
        int qtyIndex = quantityCandidate.index();

        int barcodeIndex = findBarcodeIndex(tokens, qtyIndex);
        if (barcodeIndex < 0 || barcodeIndex >= qtyIndex) {
            return overflowFallback;
        }

        String barcode = normalizeBarcodeToken(tokens[barcodeIndex]);
        String name = normalizeItemName(stripTrailingPrice(String.join(" ", Arrays.copyOfRange(tokens, barcodeIndex + 1, qtyIndex))));
        if (name == null) {
            return overflowFallback;
        }

        return new ParsedLine(barcode, name, quantityCandidate.expectedQty(), extractUnit(tokens, qtyIndex));
    }

    private QuantityCandidate findLastQuantityCandidate(String[] tokens) {
        QuantityCandidate overflowCandidate = null;
        for (int index = tokens.length - 1; index >= 0; index--) {
            String digits = extractDigits(tokens[index]);
            if (digits == null || !looksLikeQuantityToken(tokens, index, digits)) {
                continue;
            }

            Integer expectedQty = parseExpectedQty(digits);
            if (expectedQty != null) {
                return new QuantityCandidate(index, expectedQty);
            }
            if (overflowCandidate == null) {
                overflowCandidate = new QuantityCandidate(index, null);
            }
        }
        return overflowCandidate;
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

    private Integer parseExpectedQty(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean looksLikeQuantityToken(String[] tokens, int index, String digits) {
        if (isDimensionContext(tokens, index)) {
            return false;
        }

        if (digits.length() < MIN_BARCODE_LENGTH) {
            return true;
        }

        String compactToken = extractLettersAndDigits(tokens[index]);
        if (compactToken.chars().anyMatch(Character::isLetter)) {
            return true;
        }

        return index + 1 < tokens.length && looksLikeStandaloneUnitToken(tokens[index + 1]);
    }

    private boolean looksLikeStandaloneUnitToken(String token) {
        String normalized = extractLettersAndDigits(token);
        return !normalized.isBlank()
                && !isDimensionSeparatorToken(normalized)
                && normalized.length() <= 5
                && normalized.chars().anyMatch(Character::isLetter)
                && normalized.chars().noneMatch(Character::isDigit);
    }

    private boolean isDimensionContext(String[] tokens, int index) {
        return isDimensionSeparatorToken(tokenAt(tokens, index - 1))
                || isDimensionSeparatorToken(tokenAt(tokens, index + 1));
    }

    private String tokenAt(String[] tokens, int index) {
        if (index < 0 || index >= tokens.length) {
            return null;
        }
        return tokens[index];
    }

    private boolean isDimensionSeparatorToken(String token) {
        if (token == null) {
            return false;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return "x".equals(normalized) || "×".equals(normalized);
    }

    private boolean looksLikeDimensionSplit(String rawName, String unit) {
        if (isDimensionSeparatorToken(unit)) {
            return true;
        }
        if (rawName == null) {
            return false;
        }
        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(" x") || normalized.endsWith(" ×");
    }

    private String extractUnit(String[] tokens, int qtyIndex) {
        String attachedUnit = extractLettersAndDigits(tokens[qtyIndex]).replaceFirst("^\\d+", "");
        if (!attachedUnit.isBlank() && attachedUnit.chars().anyMatch(Character::isLetter)) {
            return MeasurementUnit.normalize(attachedUnit);
        }

        for (int index = qtyIndex + 1; index < tokens.length; index++) {
            String unit = extractLettersAndDigits(tokens[index]);
            if (!unit.isBlank() && unit.chars().anyMatch(Character::isLetter)) {
                return MeasurementUnit.normalize(unit);
            }
        }
        return MeasurementUnit.DEFAULT_UNIT;
    }

    private String extractLettersAndDigits(String token) {
        return token == null ? "" : token.replaceAll("[^\\p{L}\\p{N}./-]", "");
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

    private String stripTrailingPriceColumns(String line) {
        String normalized = line == null ? "" : line.trim();
        while (true) {
            String stripped = TRAILING_PRICE_COLUMN_PATTERN.matcher(normalized).replaceFirst("").trim();
            if (stripped.equals(normalized)) {
                return normalized;
            }
            normalized = TRAILING_OCR_SEPARATOR_PATTERN.matcher(stripped).replaceFirst("").trim();
        }
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

    String extractSupplierName(String text) {
        List<String> lines = normalizedLines(text);
        for (int index = 0; index < lines.size(); index++) {
            String normalized = normalizeForMatching(lines.get(index));
            if (!normalized.startsWith("dostawca")) {
                continue;
            }

            String sameLineValue = valueAfterColon(lines.get(index));
            if (isLikelySupplierName(sameLineValue)) {
                return sameLineValue;
            }

            for (int nextIndex = index + 1; nextIndex < Math.min(lines.size(), index + 6); nextIndex++) {
                String candidate = lines.get(nextIndex).trim();
                if (isLikelySupplierName(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    String extractCommercialDocumentNumber(String text) {
        Matcher matcher = COMMERCIAL_DOCUMENT_PATTERN.matcher(normalizeWhitespace(text == null ? "" : text));
        if (matcher.find()) {
            return matcher.group("number");
        }
        return null;
    }

    String extractWarehouseDocumentNumber(String text) {
        String normalizedText = normalizeWhitespace(text == null ? "" : text);
        Matcher explicitMatcher = WAREHOUSE_DOCUMENT_PATTERN.matcher(normalizedText);
        if (explicitMatcher.find()) {
            return explicitMatcher.group("number");
        }

        Matcher documentMatcher = DOCUMENT_NUMBER_PATTERN.matcher(normalizedText);
        while (documentMatcher.find()) {
            String number = documentMatcher.group("number");
            if (number != null && number.toUpperCase(Locale.ROOT).startsWith("PZ/")) {
                return number;
            }
        }
        return null;
    }

    private List<String> normalizedLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
                .map(this::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String valueAfterColon(String value) {
        if (value == null) {
            return null;
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex < 0 || colonIndex + 1 >= value.length()) {
            return null;
        }
        String normalized = value.substring(colonIndex + 1).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isLikelySupplierName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalizeForMatching(value);
        if (normalized.startsWith("ul.")
                || normalized.startsWith("nip")
                || normalized.startsWith("magazyn")
                || normalized.startsWith("lp.")
                || normalized.contains("data operacji")
                || normalized.contains("dokument handlowy")) {
            return false;
        }
        long letters = value.chars().filter(Character::isLetter).count();
        return letters >= 4;
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
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    private String normalizeWhitespace(String line) {
        return line.replace('\u00A0', ' ').replaceAll("\\s{2,}", " ").trim();
    }

    private String normalizeForMatching(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    private record ParsedLine(String barcode, String name, Integer expectedQty, String unit) {
    }

    private record QuantityCandidate(int index, Integer expectedQty) {
    }

    private record PositionSortedBlockStart(String barcode, String initialFragment) {
    }

    private record PositionSortedBlock(String barcode, List<IndexedFragment> fragments) {
    }

    private record IndexedFragment(int lineIndex, String text) {
    }
}
