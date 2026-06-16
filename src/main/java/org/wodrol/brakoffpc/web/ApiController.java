package org.wodrol.brakoffpc.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.wodrol.brakoffpc.delivery.ActiveDeliveryResponse;
import org.wodrol.brakoffpc.delivery.CurrentDeliveryResponse;
import org.wodrol.brakoffpc.delivery.DeliveryMonitorResponse;
import org.wodrol.brakoffpc.delivery.DeliveryService;
import org.wodrol.brakoffpc.delivery.DeviceStateResponse;
import org.wodrol.brakoffpc.delivery.ScanUpdateRequest;
import org.wodrol.brakoffpc.delivery.ScanUpdateResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final DeliveryService deliveryService;

    public ApiController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/delivery/current")
    public ResponseEntity<CurrentDeliveryResponse> getCurrentDelivery() {
        return deliveryService.getCurrentDeliveryResponse()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/deliveries/active")
    public List<ActiveDeliveryResponse> getActiveDeliveries() {
        return deliveryService.getActiveDeliveryResponses();
    }

    @GetMapping("/deliveries/monitors")
    public List<DeliveryMonitorResponse> getActiveDeliveryMonitors() {
        return deliveryService.getActiveDeliveryMonitorResponses();
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<CurrentDeliveryResponse> getDelivery(@PathVariable String deliveryId) {
        return deliveryService.getCurrentDeliveryResponse(deliveryId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/active-delivery")
    public ResponseEntity<CurrentDeliveryResponse> getActiveDelivery() {
        return getCurrentDelivery();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("delivery", deliveryService.getActiveDelivery().orElse(null));
        response.put("rows", deliveryService.getDashboardRows());
        response.put("devices", deliveryService.getDeviceRows());
        return response;
    }

    @GetMapping("/device-state/{deviceId}")
    public ResponseEntity<List<DeviceStateResponse>> getDeviceState(@PathVariable String deviceId) {
        if (deliveryService.getActiveDelivery().isEmpty()) {
            log.warn("Zadanie stanu urzadzenia bez aktywnej dostawy deviceId={}", deviceId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(deliveryService.getDeviceState(deviceId));
    }

    @GetMapping("/deliveries/{deliveryId}/device-state/{deviceId}")
    public ResponseEntity<List<DeviceStateResponse>> getDeviceState(
            @PathVariable String deliveryId,
            @PathVariable String deviceId
    ) {
        if (deliveryService.getActiveDelivery(deliveryId).isEmpty()) {
            log.warn("Zadanie stanu urzadzenia bez aktywnej dostawy deliveryId={} deviceId={}", deliveryId, deviceId);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(deliveryService.getDeviceState(deliveryId, deviceId));
    }

    @PostMapping("/device-state")
    public ResponseEntity<ScanUpdateResult> updateDeviceState(@Valid @RequestBody ScanUpdateRequest request) {
        ScanUpdateResult result = deliveryService.applyScanUpdate(request);
        if (!result.accepted() && "NO_ACTIVE_DELIVERY".equals(result.reason())) {
            log.warn("Odrzucono update skanu: brak aktywnej dostawy deviceId={} barcode={}",
                    request.getDeviceId(), request.getBarcode());
            return ResponseEntity.badRequest().body(result);
        }
        if (!result.accepted() && "DELIVERY_MISMATCH".equals(result.reason())) {
            log.warn("Odrzucono update skanu: niezgodne deliveryId deviceId={} barcode={}",
                    request.getDeviceId(), request.getBarcode());
            return ResponseEntity.status(409).body(result);
        }
        if (!result.accepted()) {
            log.warn("Odrzucono update skanu: powod={} deviceId={} barcode={}",
                    result.reason(), request.getDeviceId(), request.getBarcode());
            return ResponseEntity.status(409).body(result);
        }
        log.info("Przyjeto update skanu deviceId={} barcode={} serverQuantity={} unchanged={}",
                request.getDeviceId(), request.getBarcode(), result.serverQuantity(), result.unchanged());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/deliveries/{deliveryId}/device-state")
    public ResponseEntity<ScanUpdateResult> updateDeviceState(
            @PathVariable String deliveryId,
            @Valid @RequestBody ScanUpdateRequest request
    ) {
        request.setDeliveryId(deliveryId);
        return updateDeviceState(request);
    }

    @PostMapping("/active-delivery/scans")
    public ResponseEntity<ScanUpdateResult> updateScan(@Valid @RequestBody ScanUpdateRequest request) {
        return updateDeviceState(request);
    }
}
