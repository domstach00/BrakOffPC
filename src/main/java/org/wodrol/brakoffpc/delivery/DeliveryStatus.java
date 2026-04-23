package org.wodrol.brakoffpc.delivery;

public final class DeliveryStatus {

    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";
    public static final String REPLACED = "REPLACED";
    public static final String FINISHED = "FINISHED";

    private DeliveryStatus() {
    }

    public static String description(String status) {
        if (ACTIVE.equals(status)) {
            return "Dostawa jest aktualnie aktywna i dostępna do dalszego skanowania.";
        }
        if (ARCHIVED.equals(status)) {
            return "Dostawa została zakończona ręcznie przed zeskanowaniem wszystkich oczekiwanych produktów.";
        }
        if (REPLACED.equals(status)) {
            return "Dostawa została zastąpiona nowym plikiem przed zeskanowaniem wszystkich oczekiwanych produktów.";
        }
        if (FINISHED.equals(status)) {
            return "Wszystkie oczekiwane produkty zostały zeskanowane przed zakończeniem lub zastąpieniem dostawy.";
        }
        return "Status dostawy.";
    }
}
