package org.wodrol.brakoffpc.delivery;

import java.util.List;

public record ScanUpdateResult(
        boolean accepted,
        boolean unchanged,
        String reason,
        int serverQuantity,
        List<DeliveryBarcodeMatchResponse> suggestedDeliveries
) {
    public ScanUpdateResult {
        suggestedDeliveries = suggestedDeliveries == null ? List.of() : List.copyOf(suggestedDeliveries);
    }

    public ScanUpdateResult(boolean accepted, boolean unchanged, String reason, int serverQuantity) {
        this(accepted, unchanged, reason, serverQuantity, List.of());
    }
}
