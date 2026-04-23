package org.wodrol.brakoffpc.delivery;

public record ScanUpdateResult(
        boolean accepted,
        boolean unchanged,
        String reason,
        int serverQuantity
) {
}
