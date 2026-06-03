package org.wodrol.brakoffpc.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.wodrol.brakoffpc.common.MeasurementUnit;
import org.wodrol.brakoffpc.desktop.AppStartupSettings;
import org.wodrol.brakoffpc.desktop.AppStartupSettingsService;
import org.wodrol.brakoffpc.desktop.WindowsAutoStartService;
import org.wodrol.brakoffpc.delivery.DashboardRow;
import org.wodrol.brakoffpc.delivery.DeliveryAdjustmentForm;
import org.wodrol.brakoffpc.delivery.DeliveryAdjustmentRow;
import org.wodrol.brakoffpc.delivery.DeliveryAdjustmentRowInput;
import org.wodrol.brakoffpc.delivery.DeliveryService;
import org.wodrol.brakoffpc.delivery.DeliveryStatus;
import org.wodrol.brakoffpc.imports.ImportDraft;
import org.wodrol.brakoffpc.imports.ImportDraftItem;
import org.wodrol.brakoffpc.imports.ImportRowForm;
import org.wodrol.brakoffpc.imports.PendingImportService;
import org.wodrol.brakoffpc.imports.PdfImportException;
import org.wodrol.brakoffpc.imports.ValidatedImportRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
@RequestMapping
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private final PendingImportService pendingImportService;
    private final DeliveryService deliveryService;
    private final AppStartupSettingsService appStartupSettingsService;
    private final WindowsAutoStartService windowsAutoStartService;
    private final String publicUrl;
    private final boolean desktopSettingsEnabled;
    private final String mobileApiToken;

    public HomeController(
            PendingImportService pendingImportService,
            DeliveryService deliveryService,
            AppStartupSettingsService appStartupSettingsService,
            WindowsAutoStartService windowsAutoStartService,
            @Value("${app.public-url:https://brakoff.mpdwodrol.com}") String publicUrl,
            @Value("${app.desktop-settings.enabled:true}") boolean desktopSettingsEnabled,
            @Value("${app.security.mobile.token}") String mobileApiToken
    ) {
        this.pendingImportService = pendingImportService;
        this.deliveryService = deliveryService;
        this.appStartupSettingsService = appStartupSettingsService;
        this.windowsAutoStartService = windowsAutoStartService;
        this.publicUrl = normalizePublicUrl(publicUrl);
        this.desktopSettingsEnabled = desktopSettingsEnabled;
        this.mobileApiToken = mobileApiToken == null ? "" : mobileApiToken.trim();
    }

    @GetMapping("/")
    public String home(
            Model model,
            @ModelAttribute("message") String message,
            @ModelAttribute("error") String error
    ) {
        List<DashboardRow> dashboardRows = deliveryService.getDashboardRows();
        model.addAttribute("activeDelivery", deliveryService.getActiveDelivery().orElse(null));
        model.addAttribute("dashboardRows", dashboardRows);
        model.addAttribute("deviceRows", deliveryService.getDeviceRows());
        model.addAttribute("publicServerUrl", publicUrl);
        model.addAttribute("mobileApiToken", mobileApiToken);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        populateStartupSettings(model);
        populateDashboardSummary(model, dashboardRows);
        return "index";
    }

    @PostMapping("/settings/startup")
    public String updateStartupSettings(
            @RequestParam(name = "openBrowserOnStartup", defaultValue = "false") boolean openBrowserOnStartup,
            @RequestParam(name = "autoStartWithWindows", defaultValue = "false") boolean autoStartWithWindows,
            RedirectAttributes redirectAttributes
    ) {
        if (!desktopSettingsEnabled) {
            redirectAttributes.addFlashAttribute("error", "Ustawienia uruchamiania są dostępne tylko w wersji desktopowej.");
            return "redirect:/";
        }

        try {
            windowsAutoStartService.setEnabled(autoStartWithWindows);
            appStartupSettingsService.save(new AppStartupSettings(openBrowserOnStartup));
            redirectAttributes.addFlashAttribute("message", "Zapisano ustawienia uruchamiania aplikacji.");
        } catch (IllegalStateException | java.io.IOException exception) {
            log.warn("Nie udalo sie zapisac ustawien uruchamiania powod={}", exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/deliveries/archive")
    public String archive(
            Model model,
            @ModelAttribute("message") String message,
            @ModelAttribute("error") String error
    ) {
        model.addAttribute("deliveries", deliveryService.getArchivedDeliveries());
        model.addAttribute("hasActiveDelivery", deliveryService.getActiveDelivery().isPresent());
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "delivery-archive";
    }

    @GetMapping("/deliveries/archive/{id}")
    public String archiveDetails(
            @PathVariable String id,
            Model model,
            RedirectAttributes redirectAttributes,
            @ModelAttribute("message") String message,
            @ModelAttribute("error") String error
    ) {
        var delivery = deliveryService.getDelivery(id);
        if (delivery.isEmpty() || DeliveryStatus.ACTIVE.equals(delivery.get().status())) {
            redirectAttributes.addFlashAttribute("error", "Nie znaleziono archiwalnej dostawy.");
            return "redirect:/deliveries/archive";
        }

        List<DashboardRow> dashboardRows = deliveryService.getDashboardRows(id);
        model.addAttribute("delivery", delivery.get());
        model.addAttribute("dashboardRows", dashboardRows);
        model.addAttribute("deviceRows", deliveryService.getDeviceRowsForDelivery(id));
        model.addAttribute("hasActiveDelivery", deliveryService.getActiveDelivery().isPresent());
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        populateDashboardSummary(model, dashboardRows);
        return "delivery-archive-details";
    }

    @GetMapping("/deliveries/edit")
    public String editDelivery(
            Model model,
            RedirectAttributes redirectAttributes,
            @ModelAttribute("message") String message,
            @ModelAttribute("error") String error
    ) {
        var activeDelivery = deliveryService.getActiveDelivery();
        if (activeDelivery.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Brak aktywnej dostawy do edycji.");
            return "redirect:/";
        }

        List<DashboardRow> dashboardRows = deliveryService.getDashboardRows();
        populateDeliveryEditModel(
                model,
                activeDelivery.get(),
                dashboardRows,
                "/deliveries/adjust",
                "/",
                "Korekta aktywnej dostawy",
                "Popraw nazwę i oczekiwaną ilość dla produktów aktywnej dostawy. Dotyczy to też pozycji, które wcześniej były poza listą. Możesz też usunąć cały wiersz razem z jego skanami.",
                "Produkty aktywnej dostawy",
                "Powrót do dashboardu",
                "Zmieniaj nazwę i oczekiwaną ilość bez przerywania pracy dostawy.",
                "Możesz też zmienić barcode albo dodać nowy rekord. Usunięcie wiersza usuwa produkt z aktywnej dostawy oraz jego skany z raportu końcowego.",
                "Po zapisaniu dashboard, raport i lista urządzeń będą przeliczone na nowo.",
                message,
                error
        );
        return "delivery-edit";
    }

    @GetMapping("/deliveries/archive/{id}/edit")
    public String editArchivedDelivery(
            @PathVariable String id,
            Model model,
            RedirectAttributes redirectAttributes,
            @ModelAttribute("message") String message,
            @ModelAttribute("error") String error
    ) {
        var delivery = deliveryService.getDelivery(id);
        if (delivery.isEmpty() || DeliveryStatus.ACTIVE.equals(delivery.get().status())) {
            redirectAttributes.addFlashAttribute("error", "Nie znaleziono archiwalnej dostawy do edycji.");
            return "redirect:/deliveries/archive";
        }

        List<DashboardRow> dashboardRows = deliveryService.getDashboardRows(id);
        populateDeliveryEditModel(
                model,
                delivery.get(),
                dashboardRows,
                "/deliveries/archive/" + id + "/adjust",
                "/deliveries/archive/" + id,
                "Korekta dostawy z archiwum",
                "Popraw dane produktów zapisanych w archiwum bez aktywowania tej dostawy. Zmiany obejmują też zapisane skany tej dostawy.",
                "Produkty archiwalnej dostawy",
                "Powrót do szczegółów archiwum",
                "Edytujesz dane zapisane w archiwum bez wznawiania tej dostawy.",
                "Możesz zmienić nazwę, ilość, barcode, dodać nowy rekord albo usunąć wiersz razem z jego zapisanymi skanami.",
                "Po zapisaniu podgląd archiwum i dane urządzeń dla tej dostawy zostaną przeliczone na nowo.",
                message,
                error
        );
        return "delivery-edit";
    }

    @PostMapping("/imports")
    public String uploadPdf(@RequestParam("pdfFile") MultipartFile pdfFile, RedirectAttributes redirectAttributes) {
        if (pdfFile.isEmpty()) {
            log.warn("Odrzucono pusty upload PDF");
            redirectAttributes.addFlashAttribute("error", "Wybierz plik PDF.");
            return "redirect:/";
        }

        try {
            ImportDraft draft = pendingImportService.createFromPdf(pdfFile);
            log.info("Przetworzono upload PDF plik={} draftId={}",
                    draft.fileName(), draft.id());
            return "redirect:/imports/" + draft.id();
        } catch (PdfImportException exception) {
            log.warn("Nie udalo sie przetworzyc PDF plik={} powod={}",
                    pdfFile.getOriginalFilename(), exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/imports/{id}")
    public String reviewImport(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        try {
            ImportDraft draft = pendingImportService.get(id);
            List<ValidatedImportRow> rows = pendingImportService.validate(draft);
            ImportRowForm form = new ImportRowForm();
            form.setRows(rows.stream().map(row -> {
                org.wodrol.brakoffpc.imports.ImportRowInput input = new org.wodrol.brakoffpc.imports.ImportRowInput();
                input.setBarcode(row.barcode());
                input.setName(row.name());
                input.setExpectedQty(row.expectedQty() == null ? "" : String.valueOf(row.expectedQty()));
                input.setUnit(row.unit());
                return input;
            }).toList());

            model.addAttribute("draft", draft);
            model.addAttribute("rows", rows);
            model.addAttribute("form", form);
            model.addAttribute("hasErrors", rows.stream().anyMatch(ValidatedImportRow::hasCriticalError));
            populateImportSummary(model, rows);
            log.info("Wczytano podglad importu draftId={} liczbaWierszy={}", id, rows.size());
            return "import-review";
        } catch (NoSuchElementException exception) {
            log.warn("Nie znaleziono draftu importu id={}", id);
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/";
        }
    }

    @PostMapping("/imports/{id}/confirm")
    public String confirmImport(
            @PathVariable String id,
            @Valid @ModelAttribute("form") ImportRowForm form,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ImportDraft draft;
        try {
            draft = pendingImportService.get(id);
        } catch (NoSuchElementException exception) {
            log.warn("Nie znaleziono draftu do zatwierdzenia id={}", id);
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/";
        }

        List<ImportDraftItem> sanitized = pendingImportService.sanitizeRows(form);

        List<ValidatedImportRow> validatedRows = pendingImportService.validateRows(sanitized);
        if (validatedRows.isEmpty() || validatedRows.stream().anyMatch(ValidatedImportRow::hasCriticalError)) {
            log.warn("Zatwierdzenie importu zablokowane draftId={} liczbaWierszy={} liczbaBlednych={}",
                    id,
                    validatedRows.size(),
                    validatedRows.stream().filter(ValidatedImportRow::hasCriticalError).count());
            ImportRowForm retryForm = new ImportRowForm();
            retryForm.setRows(sanitized.stream().map(item -> {
                org.wodrol.brakoffpc.imports.ImportRowInput input = new org.wodrol.brakoffpc.imports.ImportRowInput();
                input.setBarcode(item.barcode());
                input.setName(item.name());
                input.setExpectedQty(item.expectedQty() == null ? "" : String.valueOf(item.expectedQty()));
                input.setUnit(item.unit());
                return input;
            }).toList());

            model.addAttribute("draft", draft);
            model.addAttribute("rows", validatedRows);
            model.addAttribute("form", retryForm);
            model.addAttribute("hasErrors", true);
            model.addAttribute("error", "Czesc wierszy wymaga poprawy przed zatwierdzeniem importu.");
            populateImportSummary(model, validatedRows);
            return "import-review";
        }

        deliveryService.activate(draft, sanitized);
        pendingImportService.deleteDraft(id);
        log.info("Zatwierdzono import draftId={} liczbaPozycji={}", id, sanitized.size());
        redirectAttributes.addFlashAttribute("message", "Dostawa zostala aktywowana.");
        return "redirect:/";
    }

    @PostMapping("/deliveries/reset")
    public String resetDelivery(RedirectAttributes redirectAttributes) {
        deliveryService.resetActiveDelivery();
        log.info("Uzytkownik zresetowal aktywna dostawe");
        redirectAttributes.addFlashAttribute("message", "Aktywna dostawa została zakończona.");
        return "redirect:/";
    }

    @PostMapping("/deliveries/adjust")
    public String adjustDelivery(
            @ModelAttribute("deliveryAdjustmentForm") DeliveryAdjustmentForm form,
            RedirectAttributes redirectAttributes
    ) {
        try {
            List<DeliveryAdjustmentRow> rows = sanitizeAdjustmentRows(form);
            deliveryService.applyManualCorrections(rows);
            redirectAttributes.addFlashAttribute("message", "Zapisano ręczną korektę aktywnej dostawy.");
            return "redirect:/";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Nie udało się zapisać ręcznej korekty dostawy powod={}", exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/deliveries/edit";
        }
    }

    @PostMapping("/deliveries/archive/{id}/adjust")
    public String adjustArchivedDelivery(
            @PathVariable String id,
            @ModelAttribute("deliveryAdjustmentForm") DeliveryAdjustmentForm form,
            RedirectAttributes redirectAttributes
    ) {
        try {
            List<DeliveryAdjustmentRow> rows = sanitizeAdjustmentRows(form);
            deliveryService.applyManualCorrections(id, rows);
            redirectAttributes.addFlashAttribute("message", "Zapisano ręczną korektę archiwalnej dostawy.");
            return "redirect:/deliveries/archive/" + id;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Nie udało się zapisać ręcznej korekty archiwalnej dostawy id={} powod={}", id, exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/deliveries/archive/" + id + "/edit";
        }
    }

    @PostMapping("/deliveries/archive/{id}/continue")
    public String continueArchivedDelivery(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            deliveryService.continueArchivedDelivery(id);
            redirectAttributes.addFlashAttribute("message", "Archiwalna dostawa została przywrócona jako aktywna.");
            return "redirect:/";
        } catch (IllegalStateException exception) {
            log.warn("Nie udało się przywrócić archiwalnej dostawy id={} powod={}", id, exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/deliveries/archive/" + id;
        }
    }

    @PostMapping("/deliveries/archive/delete")
    public String deleteArchivedDeliveries(
            @RequestParam(name = "deliveryIds", required = false) List<String> deliveryIds,
            RedirectAttributes redirectAttributes
    ) {
        if (deliveryIds == null || deliveryIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Wybierz dostawy do usunięcia.");
            return "redirect:/deliveries/archive";
        }

        deliveryService.deleteArchivedDeliveries(deliveryIds);
        redirectAttributes.addFlashAttribute("message", "Usunięto wybrane dostawy z archiwum.");
        return "redirect:/deliveries/archive";
    }

    @GetMapping("/deliveries/report.pdf")
    public ResponseEntity<byte[]> downloadReport() {
        String reportFileName = deliveryService.getActiveDelivery()
                .map(activeDelivery -> "raport-dostawy-" + sanitizeReportFileName(activeDelivery.sourceFileName()) + ".pdf")
                .orElse("raport-dostawy.pdf");
        byte[] content = deliveryService.generateReportPdf();
        log.info("Wygenerowano raport PDF plik={} rozmiarBajtow={}", reportFileName, content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(reportFileName).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/deliveries/archive/{id}/report.pdf")
    public ResponseEntity<byte[]> downloadArchivedReport(@PathVariable String id) {
        var delivery = deliveryService.getDelivery(id)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono dostawy do raportu."));
        String reportFileName = "raport-dostawy-" + sanitizeReportFileName(delivery.sourceFileName()) + ".pdf";
        byte[] content = deliveryService.generateReportPdf(id);
        log.info("Wygenerowano raport PDF dla archiwalnej dostawy id={} plik={} rozmiarBajtow={}",
                id, reportFileName, content.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(reportFileName).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    private String sanitizeReportFileName(String sourceFileName) {
        if (sourceFileName == null || sourceFileName.isBlank()) {
            return "plik-zrodlowy";
        }

        String sanitized = sourceFileName.trim()
                .replace('\\', '-')
                .replace('/', '-')
                .replaceAll("\\.pdf$", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();

        return sanitized.isEmpty() ? "plik-zrodlowy" : sanitized;
    }

    private DeliveryAdjustmentForm buildDeliveryAdjustmentForm(List<DashboardRow> dashboardRows) {
        DeliveryAdjustmentForm form = new DeliveryAdjustmentForm();
        form.setRows(dashboardRows.stream().map(row -> {
            DeliveryAdjustmentRowInput input = new DeliveryAdjustmentRowInput();
            input.setOriginalBarcode(row.barcode());
            input.setBarcode(row.barcode());
            input.setName(row.name());
            input.setExpectedQty(String.valueOf(row.expectedQty()));
            input.setUnit(row.unit());
            return input;
        }).toList());
        return form;
    }

    private void populateDeliveryEditModel(
            Model model,
            org.wodrol.brakoffpc.delivery.DeliveryRecord delivery,
            List<DashboardRow> dashboardRows,
            String submitAction,
            String backHref,
            String pageEyebrow,
            String pageLead,
            String tableTitle,
            String backLabel,
            String toolbarTitle,
            String toolbarHint,
            String saveHint,
            String message,
            String error
    ) {
        model.addAttribute("delivery", delivery);
        model.addAttribute("dashboardRows", dashboardRows);
        model.addAttribute("deliveryAdjustmentForm", buildDeliveryAdjustmentForm(dashboardRows));
        model.addAttribute("submitAction", submitAction);
        model.addAttribute("backHref", backHref);
        model.addAttribute("pageEyebrow", pageEyebrow);
        model.addAttribute("pageLead", pageLead);
        model.addAttribute("tableTitle", tableTitle);
        model.addAttribute("backLabel", backLabel);
        model.addAttribute("toolbarTitle", toolbarTitle);
        model.addAttribute("toolbarHint", toolbarHint);
        model.addAttribute("saveHint", saveHint);
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        populateDashboardSummary(model, dashboardRows);
    }

    private List<DeliveryAdjustmentRow> sanitizeAdjustmentRows(DeliveryAdjustmentForm form) {
        if (form == null || form.getRows() == null) {
            return List.of();
        }

        List<DeliveryAdjustmentRow> rows = new ArrayList<>();
        for (DeliveryAdjustmentRowInput row : form.getRows()) {
            if (row == null) {
                continue;
            }
            rows.add(new DeliveryAdjustmentRow(
                    normalize(row.getOriginalBarcode()),
                    normalize(row.getBarcode()),
                    normalize(row.getName()),
                    parseExpectedQty(row.getExpectedQty()),
                    MeasurementUnit.normalize(row.getUnit()),
                    row.isDeleted()
            ));
        }
        return rows;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int parseExpectedQty(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return -1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void populateImportSummary(Model model, List<ValidatedImportRow> rows) {
        model.addAttribute("importRowCount", rows.size());
        model.addAttribute("importExpectedQtyTotal", formatImportQuantityTotals(rows));
    }

    private String formatImportQuantityTotals(List<ValidatedImportRow> rows) {
        Map<String, Integer> totalsByUnit = new LinkedHashMap<>();
        for (ValidatedImportRow row : rows) {
            if (row.expectedQty() == null || row.expectedQty() < 0 || row.hasCriticalError()) {
                continue;
            }
            totalsByUnit.merge(MeasurementUnit.normalize(row.unit()), row.expectedQty(), Integer::sum);
        }
        return formatQuantityTotals(totalsByUnit);
    }

    private String formatQuantityTotals(Map<String, Integer> totalsByUnit) {
        if (totalsByUnit.isEmpty()) {
            return "0";
        }
        return totalsByUnit.entrySet().stream()
                .map(entry -> MeasurementUnit.format(entry.getValue(), entry.getKey()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private void populateDashboardSummary(Model model, List<DashboardRow> rows) {
        int rowCount = rows.size();

        Map<String, Integer> expectedTotals = groupDashboardTotals(rows, DashboardRow::expectedQty);
        Map<String, Integer> scannedTotals = groupDashboardTotals(rows, DashboardRow::scannedQty);
        Map<String, Integer> differenceTotals = groupDashboardTotals(rows, DashboardRow::difference);

        model.addAttribute("dashboardRowCount", rowCount);
        model.addAttribute("dashboardExpectedTotal", formatQuantityTotals(expectedTotals));
        model.addAttribute("dashboardScannedTotal", formatQuantityTotals(scannedTotals));
        model.addAttribute("dashboardScannedTotalZero", totalsAreZero(scannedTotals));
        model.addAttribute("dashboardDifferenceTotalZero", totalsAreZero(differenceTotals));
        model.addAttribute("dashboardDifferenceLabel", formatDashboardDifferenceLabel(differenceTotals));
    }

    private Map<String, Integer> groupDashboardTotals(
            List<DashboardRow> rows,
            java.util.function.ToIntFunction<DashboardRow> quantityExtractor
    ) {
        Map<String, Integer> totalsByUnit = new LinkedHashMap<>();
        for (DashboardRow row : rows) {
            totalsByUnit.merge(MeasurementUnit.normalize(row.unit()), quantityExtractor.applyAsInt(row), Integer::sum);
        }
        return totalsByUnit;
    }

    private boolean totalsAreZero(Map<String, Integer> totalsByUnit) {
        return totalsByUnit.values().stream().allMatch(quantity -> quantity == 0);
    }

    private void populateStartupSettings(Model model) {
        model.addAttribute("desktopSettingsVisible", desktopSettingsEnabled);
        AppStartupSettings startupSettings = appStartupSettingsService.load();
        model.addAttribute("openBrowserOnStartup", startupSettings.openBrowserOnStartup());
        model.addAttribute("windowsAutoStartEnabled", windowsAutoStartService.isEnabled());
        model.addAttribute("windowsAutoStartAvailable", windowsAutoStartService.isConfigurable());
        model.addAttribute("windowsAutoStartHint", windowsAutoStartService.availabilityHint());
    }

    private String normalizePublicUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://brakoff.mpdwodrol.com";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private String formatDashboardDifferenceLabel(Map<String, Integer> differenceTotals) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        Map<String, Integer> excess = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : differenceTotals.entrySet()) {
            if (entry.getValue() > 0) {
                missing.put(entry.getKey(), entry.getValue());
            }
            if (entry.getValue() < 0) {
                excess.put(entry.getKey(), Math.abs(entry.getValue()));
            }
        }

        List<String> parts = new ArrayList<>();
        if (!missing.isEmpty()) {
            parts.add(formatQuantityTotals(missing) + " brak");
        }
        if (!excess.isEmpty()) {
            parts.add(formatQuantityTotals(excess) + " nadmiar");
        }
        return parts.isEmpty() ? "Różnica: 0" : "Różnica: " + String.join(", ", parts);
    }
}
