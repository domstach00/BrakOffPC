package org.wodrol.brakoffpc.imports;

public class ImportRowInput {

    private String barcode;
    private String name;
    private String expectedQty;
    private boolean deleted;

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

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
