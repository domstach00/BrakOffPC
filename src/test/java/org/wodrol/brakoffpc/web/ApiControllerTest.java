package org.wodrol.brakoffpc.web;

import org.junit.jupiter.api.Test;
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
}
