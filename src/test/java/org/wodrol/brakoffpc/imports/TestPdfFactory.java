package org.wodrol.brakoffpc.imports;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

final class TestPdfFactory {

    private static final PDType1Font TEST_FONT = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private TestPdfFactory() {
    }

    static byte[] createTextPdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(TEST_FONT, 9);
                contentStream.newLineAtOffset(36, page.getMediaBox().getHeight() - 36);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -12);
                }
                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    static byte[] createImageOnlyPdf(List<String> lines) throws IOException {
        int width = 1800;
        int lineHeight = 54;
        int padding = 72;
        int height = Math.max(600, padding * 2 + lineHeight * (lines.size() + 1));

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 38));

            FontMetrics metrics = graphics.getFontMetrics();
            int y = padding + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, padding, y);
                y += lineHeight;
            }
        } finally {
            graphics.dispose();
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (var contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, 0, 0, width, height);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
