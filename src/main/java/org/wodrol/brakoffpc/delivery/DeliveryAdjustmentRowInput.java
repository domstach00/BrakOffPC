package org.wodrol.brakoffpc.delivery;

public class DeliveryAdjustmentRowInput {

    private String originalBarcode;
    private String barcode;
    private String name;
    private String expectedQty;
    private String unit;
    private boolean deleted;

    public String getOriginalBarcode() {
        return originalBarcode;
    }

    public void setOriginalBarcode(String originalBarcode) {
        this.originalBarcode = originalBarcode;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(String expectedQty) {
        this.expectedQty = expectedQty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
