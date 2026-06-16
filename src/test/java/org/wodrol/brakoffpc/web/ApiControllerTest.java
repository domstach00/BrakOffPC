package org.wodrol.brakoffpc.web;

import org.junit.jupiter.api.Test;
import org.wodrol.brakoffpc.delivery.ActiveDeliveryResponse;
import org.wodrol.brakoffpc.delivery.DeliveryMonitorResponse;
import org.wodrol.brakoffpc.delivery.DeliveryService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiControllerTest {

    @Test
    void returnsDashboardPayloadWhenThereIsNoActiveDelivery() {
        DeliveryService deliveryService = mock(DeliveryService.class);
        ApiController controller = new ApiController(deliveryService);

        when(deliveryService.getActiveDelivery()).thenReturn(Optional.empty());
        when(deliveryService.getDashboardRows()).thenReturn(List.of());
        when(deliveryService.getDeviceRows()).thenReturn(List.of());

        Map<String, Object> response = controller.getDashboard();

        assertNull(response.get("delivery"));
        assertEquals(List.of(), response.get("rows"));
        assertEquals(List.of(), response.get("devices"));
    }

    @Test
    void returnsActiveDeliveryMonitorPayload() {
        DeliveryService deliveryService = mock(DeliveryService.class);
        ApiController controller = new ApiController(deliveryService);
        List<DeliveryMonitorResponse> monitors = List.of(new DeliveryMonitorResponse(
                new ActiveDeliveryResponse("delivery-1", "source.pdf", "2026-06-16T10:00:00.000+0000", 2),
                List.of(),
                List.of()
        ));

        when(deliveryService.getActiveDeliveryMonitorResponses()).thenReturn(monitors);

        assertEquals(monitors, controller.getActiveDeliveryMonitors());
    }
}
