package org.wodrol.brakoffpc.imports;

import java.util.ArrayList;
import java.util.List;

public class ImportRowForm {

    private String supplierName;
    private String commercialDocumentNumber;
    private String warehouseDocumentNumber;
    private List<ImportRowInput> rows = new ArrayList<>();

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getCommercialDocumentNumber() {
        return commercialDocumentNumber;
    }

    public void setCommercialDocumentNumber(String commercialDocumentNumber) {
        this.commercialDocumentNumber = commercialDocumentNumber;
    }

    public String getWarehouseDocumentNumber() {
        return warehouseDocumentNumber;
    }

    public void setWarehouseDocumentNumber(String warehouseDocumentNumber) {
        this.warehouseDocumentNumber = warehouseDocumentNumber;
    }

    public List<ImportRowInput> getRows() {
        return rows;
    }

    public void setRows(List<ImportRowInput> rows) {
        this.rows = rows;
    }
}
