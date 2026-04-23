package org.wodrol.brakoffpc.imports;

public enum ImportRowStatus {
    OK("OK", false),
    MISSING_BARCODE("Brak barcode", true),
    INVALID_BARCODE("Niepoprawny barcode", true),
    INVALID_NAME("Brak nazwy", true),
    INVALID_QUANTITY("Niepoprawna ilosc", true),
    DUPLICATE_BARCODE("Duplikat barcode", true);

    private final String label;
    private final boolean critical;

    ImportRowStatus(String label, boolean critical) {
        this.label = label;
        this.critical = critical;
    }

    public String label() {
        return label;
    }

    public boolean critical() {
        return critical;
    }
}
