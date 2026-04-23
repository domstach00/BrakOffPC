package org.wodrol.brakoffpc.delivery;

import java.util.ArrayList;
import java.util.List;

public class DeliveryAdjustmentForm {

    private List<DeliveryAdjustmentRowInput> rows = new ArrayList<>();

    public List<DeliveryAdjustmentRowInput> getRows() {
        return rows;
    }

    public void setRows(List<DeliveryAdjustmentRowInput> rows) {
        this.rows = rows;
    }
}
