package org.wodrol.brakoffpc.delivery;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;

final class ReportFonts {
    private ReportFonts() {}

    static Font font(float size, int style) {
        return new Font(Holder.BASE_FONT, size, style);
    }

    private static final class Holder {
        private static final BaseFont BASE_FONT = load();

        private static BaseFont load() {
            try (var stream = new ClassPathResource("fonts/LiberationSans-Regular.ttf").getInputStream()) {
                return BaseFont.createFont("LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                        true, stream.readAllBytes(), null);
            } catch (Exception exception) {
                throw new IllegalStateException("Nie udało się wczytać czcionki raportu.", exception);
            }
        }
    }
}
