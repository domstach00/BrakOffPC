package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.pdmodel.PDDocument;

@FunctionalInterface
public interface PdfOcrService {

    String extractText(PDDocument document);
}
