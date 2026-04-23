package org.wodrol.brakoffpc.imports;

import java.util.ArrayList;
import java.util.List;

public class ImportRowForm {

    private List<ImportRowInput> rows = new ArrayList<>();

    public List<ImportRowInput> getRows() {
        return rows;
    }

    public void setRows(List<ImportRowInput> rows) {
        this.rows = rows;
    }
}
