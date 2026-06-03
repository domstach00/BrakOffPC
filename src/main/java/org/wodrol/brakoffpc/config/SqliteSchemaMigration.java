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
}
