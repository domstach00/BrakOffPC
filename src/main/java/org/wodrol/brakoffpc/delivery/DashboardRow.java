package org.wodrol.brakoffpc.delivery;

public record DashboardRow(
        String barcode,
        String name,
        int expectedQty,
        int scannedQty,
        int difference,
        boolean unordered
) {
    public String status() {
        if (unordered) {
            return "Niezamowiony";
        }
        if (difference > 0) {
            return "Brakuje";
        }
        if (difference < 0) {
            return "Nadmiar";
        }
        return "OK";
    }
}
