package org.wodrol.brakoffpc.delivery;

import java.util.ArrayList;
import java.util.List;

public class DeliveryAdjustmentForm {

    private String supplierName;
    private String commercialDocumentNumber;
    private String warehouseDocumentNumber;
    private List<DeliveryAdjustmentRowInput> rows = new ArrayList<>();

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

    public List<DeliveryAdjustmentRowInput> getRows() {
        return rows;
    }

    public void setRows(List<DeliveryAdjustmentRowInput> rows) {
        this.rows = rows;
    }
}
