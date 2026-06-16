package org.wodrol.brakoffpc.delivery;

import java.util.List;

public record DeliveryMonitorResponse(
        ActiveDeliveryResponse delivery,
        List<DashboardRow> rows,
        List<DeviceSummaryRow> deviceRows
) {
    public DeliveryMonitorResponse {
        rows = rows == null ? List.of() : List.copyOf(rows);
        deviceRows = deviceRows == null ? List.of() : List.copyOf(deviceRows);
    }
}
