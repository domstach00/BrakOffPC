package org.wodrol.brakoffpc.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SqliteSchemaMigration {

    @Bean
    ApplicationRunner migrateDeviceScanSchema(JdbcClient jdbcClient) {
        return args -> {
            List<String> deviceScanColumns = jdbcClient.sql("pragma table_info('device_scan')")
                    .query((rs, rowNum) -> rs.getString("name"))
                    .list();

            if (!deviceScanColumns.contains("device_name")) {
                jdbcClient.sql("alter table device_scan add column device_name TEXT").update();
            }
            if (!deviceScanColumns.contains("item_name")) {
                jdbcClient.sql("alter table device_scan add column item_name TEXT").update();
            }

            addUnitColumnIfMissing(jdbcClient, "pending_import_item");
            addUnitColumnIfMissing(jdbcClient, "delivery_item");
            addTextColumnIfMissing(jdbcClient, "pending_import", "supplier_name");
            addTextColumnIfMissing(jdbcClient, "pending_import", "commercial_document_number");
            addTextColumnIfMissing(jdbcClient, "pending_import", "warehouse_document_number");
            addTextColumnIfMissing(jdbcClient, "delivery", "supplier_name");
            addTextColumnIfMissing(jdbcClient, "delivery", "commercial_document_number");
            addTextColumnIfMissing(jdbcClient, "delivery", "warehouse_document_number");
        };
    }

    private void addUnitColumnIfMissing(JdbcClient jdbcClient, String tableName) {
        List<String> columns = jdbcClient.sql("pragma table_info('" + tableName + "')")
                .query((rs, rowNum) -> rs.getString("name"))
                .list();

        if (!columns.contains("unit")) {
            jdbcClient.sql("alter table " + tableName + " add column unit TEXT NOT NULL DEFAULT 'szt'").update();
        }
    }

    private void addTextColumnIfMissing(JdbcClient jdbcClient, String tableName, String columnName) {
        List<String> columns = jdbcClient.sql("pragma table_info('" + tableName + "')")
                .query((rs, rowNum) -> rs.getString("name"))
                .list();

        if (!columns.contains(columnName)) {
            jdbcClient.sql("alter table " + tableName + " add column " + columnName + " TEXT").update();
        }
    }
}
