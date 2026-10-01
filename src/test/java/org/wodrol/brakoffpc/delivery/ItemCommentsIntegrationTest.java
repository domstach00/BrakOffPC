package org.wodrol.brakoffpc.delivery;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.desktop-settings.enabled=false",
        "app.security.mobile.token=comments-test-token",
        "app.security.operator.username=comments-operator",
        "app.security.operator.password=comments-password"
})
@AutoConfigureMockMvc
@Transactional
class ItemCommentsIntegrationTest {
    private static final Path DATABASE = databasePath();
    private static final String COMMENTS_URL = "/api/deliveries/delivery-a/items/00123/comments";
    private static final String TOKEN = "Bearer comments-test-token";
    private static final String PAYLOAD = """
            {"commentId":"6f465a48-7896-43b1-ae27-4074b627ba07","deviceId":"phone-1",
             "deviceName":"Magazyn 1","text":"Błędny odczyt z PDF. Zmień kod i nazwę.\\nZażółć gęślą jaźń.",
             "suggestedBarcode":"00999","suggestedName":"Śruba właściwa"}
            """;

    @Autowired MockMvc mvc;
    @Autowired DeliveryRepository repository;
    @Autowired DeliveryService service;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.sqlite.path", () -> DATABASE.toString());
    }

    private static Path databasePath() {
        try {
            Path path = Files.createTempFile("brakoff-comments-test-", ".db");
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void seed() {
        repository.save(new DeliveryRecord("delivery-a", "dostawa.pdf", DeliveryStatus.ACTIVE,
                Instant.now(), Instant.now(), List.of(new DeliveryItem("delivery-a", "00123", "Śruba z PDF", 5))));
        repository.save(new DeliveryRecord("delivery-b", "inna.pdf", DeliveryStatus.ACTIVE,
                Instant.now(), Instant.now(), List.of(new DeliveryItem("delivery-b", "00123", "Inny produkt", 7))));
        repository.upsertScan("delivery-a", new DeviceScanState("phone-1", "Magazyn 1", "00123",
                "Śruba z PDF", 5, 4, Instant.parse("2026-10-01T09:00:00Z")));
    }

    @Test
    void sharesCommentsAcrossPhonesWithoutChangingQuantitiesAndRetriesOnlyOnce() throws Exception {
        var scan = repository.findScan("delivery-a", "phone-1", "00123").orElseThrow();
        String first = mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("barcode").value("00123"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(content().json(first));
        assertEquals(1, repository.findComments("delivery-a").size());
        assertEquals(scan, repository.findScan("delivery-a", "phone-1", "00123").orElseThrow());
        mvc.perform(get("/api/deliveries/delivery-a").header("Authorization", TOKEN))
                .andExpect(jsonPath("items[0].comments[0].deviceId").value("phone-1"))
                .andExpect(jsonPath("items[0].scannedQty").value(5));
        mvc.perform(get(COMMENTS_URL).header("Authorization", TOKEN))
                .andExpect(jsonPath("$[0].suggestedBarcode").value("00999"));
        mvc.perform(get("/api/deliveries/delivery-b").header("Authorization", TOKEN))
                .andExpect(jsonPath("items[0].comments").isEmpty());
        mvc.perform(get("/api/deliveries/monitors").header("Authorization", TOKEN))
                .andExpect(content().string(containsString("Błędny odczyt")));
    }

    @Test
    void validatesAuthorizationPayloadAndTargetAndRejectsChangedRetry() throws Exception {
        mvc.perform(post(COMMENTS_URL).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isUnauthorized());
        for (String invalid : List.of(PAYLOAD.replace("phone-1", "  "),
                PAYLOAD.replace("00999", "ABC"), PAYLOAD.replace("Magazyn 1", "a".repeat(201)),
                PAYLOAD.replace("6f465a48-7896-43b1-ae27-4074b627ba07", "bad-id"),
                PAYLOAD.replace("Błędny odczyt z PDF. Zmień kod i nazwę.\\nZażółć gęślą jaźń.", " ")) ) {
            mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(COMMENTS_URL.replace("00123", "unknown")).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isNotFound()).andExpect(jsonPath("reason").value("ITEM_NOT_FOUND"));
        mvc.perform(post(COMMENTS_URL.replace("delivery-a", "missing")).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isNotFound()).andExpect(jsonPath("reason").value("DELIVERY_NOT_ACTIVE"));
        mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());
        mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("phone-1", "phone-2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("reason").value("COMMENT_ID_CONFLICT"));
        mvc.perform(post(COMMENTS_URL.replace("delivery-a", "delivery-b")).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isConflict());
    }

    @Test
    void preservesCommentsThroughBarcodeAndNameCorrectionArchiveAndIdenticalRetry() throws Exception {
        mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());
        service.applyManualCorrections("delivery-a", List.of(
                new DeliveryAdjustmentRow("00123", "00999", "Śruba właściwa", 5, "szt", false)));
        ItemComment comment = repository.findComments("delivery-a").getFirst();
        assertEquals("00999", comment.barcode());
        assertEquals("00123", comment.originalBarcode());
        assertEquals("Śruba z PDF", comment.originalName());
        assertEquals(5, service.getDashboardRows("delivery-a").getFirst().scannedQty());
        mvc.perform(get(COMMENTS_URL.replace("00123", "00999")).header("Authorization", TOKEN))
                .andExpect(jsonPath("$[0].originalBarcode").value("00123"));
        service.closeDelivery("delivery-a");
        mvc.perform(post(COMMENTS_URL).header("Authorization", TOKEN).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("barcode").value("00999"));
        mvc.perform(post(COMMENTS_URL.replace("00123", "00999")).header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD.replace("6f465a48-7896-43b1-ae27-4074b627ba07", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        try (var pdf = Loader.loadPDF(service.generateReportPdf("delivery-a"))) {
            String text = new PDFTextStripper().getText(pdf);
            assertFalse(text.contains("Komentarze do produktów"));
            assertFalse(text.contains("Zażółć gęślą jaźń."));
        }
        try (var pdf = Loader.loadPDF(service.generateReportPdf("delivery-a", true))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Komentarze do produktów"));
            assertTrue(text.contains("Zażółć gęślą jaźń."));
            assertTrue(text.contains("00999 — Śruba właściwa"));
            assertTrue(text.contains("Proponowany barcode: 00999"));
            assertTrue(text.contains("Dane w chwili zgłoszenia: 00123 — Śruba z PDF"));
        }
    }

    @Test
    void rendersCommentsSafelyInDashboardDetailsAndEditorAndKeepsDeletedProductNotes() throws Exception {
        service.addComment("delivery-a", "00123", new ItemCommentRequest(UUID.randomUUID().toString(),
                "phone-2", "Telefon 2", "<script>alert('x')</script>\nZły barcode", "00999", "Śruba właściwa"));
        service.addComment("delivery-a", "00123", new ItemCommentRequest(UUID.randomUUID().toString(),
                "phone-3", "Telefon 3", "Drugi komentarz", null, null));
        MockHttpSession session = login();
        for (String page : List.of("/", "/deliveries/delivery-a", "/deliveries/delivery-a/edit")) {
            String html = mvc.perform(get(page).session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("&lt;script&gt;"));
            assertTrue(html.contains("Proponowany barcode"));
            assertTrue(html.contains("Komentarze (2)"));
            assertTrue(html.contains("data-comment-key=\"delivery-a:00123\""));
            assertFalse(html.contains("<script>alert('x')</script>"));
        }
        for (String page : List.of("/", "/deliveries/delivery-a")) {
            String html = mvc.perform(get(page).session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertTrue(html.contains("Pobierz raport z komentarzami"));
            assertTrue(html.contains("report.pdf?includeComments=true"));
        }
        service.applyManualCorrections("delivery-a", List.of(
                new DeliveryAdjustmentRow("00123", "00123", "Śruba z PDF", 5, "szt", true)));
        mvc.perform(get("/deliveries/delivery-a").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Komentarze do usuniętych pozycji")))
                .andExpect(content().string(containsString("Zły barcode")));
        service.closeDelivery("delivery-a");
        mvc.perform(get("/deliveries/archive/delivery-a").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Zły barcode")));
        repository.deleteDeliveries(List.of("delivery-a", "delivery-b"));
        assertTrue(repository.findComments("delivery-a").isEmpty());
        assertTrue(repository.findById("delivery-b").isPresent());
    }

    @Test
    void supportsUnorderedProductsAndLongMultilinePdfComments() throws Exception {
        repository.upsertScan("delivery-a", new DeviceScanState("phone-2", "Telefon 2", "00888", "Spoza listy", 1, 1, Instant.now()));
        service.addComment("delivery-a", "00888", new ItemCommentRequest(UUID.randomUUID().toString(),
                "phone-2", null, "Długa uwaga o błędzie.\n".repeat(75) + "Koniec komentarza.", null, null));
        assertEquals(1, service.getDashboardRows("delivery-a").stream().filter(DashboardRow::unordered).findFirst().orElseThrow().comments().size());
        try (var pdf = Loader.loadPDF(service.generateReportPdf("delivery-a", true))) {
            assertTrue(pdf.getNumberOfPages() > 1);
            assertTrue(new PDFTextStripper().getText(pdf).contains("Koniec komentarza."));
        }
    }

    @Test
    void downloadOptionControlsCommentsAndUsesDistinctFileName() throws Exception {
        service.addComment("delivery-a", "00123", new ItemCommentRequest(UUID.randomUUID().toString(),
                "phone-1", "Magazyn 1", "Komentarz tylko w pełnym raporcie", null, null));
        MockHttpSession session = login();

        var regularResponse = mvc.perform(get("/deliveries/delivery-a/report.pdf").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("raport-dostawy-dostawa.pdf")))
                .andReturn().getResponse();
        try (var pdf = Loader.loadPDF(regularResponse.getContentAsByteArray())) {
            assertFalse(new PDFTextStripper().getText(pdf).contains("Komentarz tylko w pełnym raporcie"));
        }

        var commentsResponse = mvc.perform(get("/deliveries/delivery-a/report.pdf")
                        .param("includeComments", "true").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("raport-dostawy-dostawa-z-komentarzami.pdf")))
                .andReturn().getResponse();
        try (var pdf = Loader.loadPDF(commentsResponse.getContentAsByteArray())) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("Komentarz tylko w pełnym raporcie"));
        }
    }

    private MockHttpSession login() throws Exception {
        var loginPage = mvc.perform(get("/login")).andReturn();
        var session = (MockHttpSession) loginPage.getRequest().getSession();
        var csrf = (CsrfToken) loginPage.getRequest().getAttribute("_csrf");
        mvc.perform(post("/login").session(session).param(csrf.getParameterName(), csrf.getToken())
                        .param("username", "comments-operator").param("password", "comments-password"))
                .andExpect(status().isFound());
        return session;
    }
}
