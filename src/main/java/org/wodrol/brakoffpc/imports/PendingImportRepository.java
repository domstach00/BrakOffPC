package org.wodrol.brakoffpc.imports;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PendingImportRepository {

    private final JdbcClient jdbcClient;

    public PendingImportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(ImportDraft draft) {
        jdbcClient.sql("""
                insert into pending_import (id, file_name, status, error_message, created_at)
                values (?, ?, ?, ?, ?)
                """)
                .params(draft.id(), draft.fileName(), draft.status(), draft.errorMessage(), draft.createdAt().toString())
                .update();

        for (ImportDraftItem item : draft.items()) {
            jdbcClient.sql("""
                    insert into pending_import_item (import_id, row_order, barcode, name, expected_qty, unit)
                    values (?, ?, ?, ?, ?, ?)
                    """)
                    .params(draft.id(), item.rowOrder(), item.barcode(), item.name(), item.expectedQty(), item.unit())
                    .update();
        }
    }

    public Optional<ImportDraft> findById(String id) {
        List<ImportDraft> result = jdbcClient.sql("""
                select id, file_name, status, error_message, created_at
                from pending_import
                where id = ?
                """)
                .param(id)
                .query(this::mapDraftHeader)
                .list();

        if (result.isEmpty()) {
            return Optional.empty();
        }

        ImportDraft draft = result.getFirst();
        return Optional.of(new ImportDraft(
                draft.id(),
                draft.fileName(),
                draft.status(),
                draft.errorMessage(),
                draft.createdAt(),
                findItems(id)
        ));
    }

    public void deleteById(String id) {
        jdbcClient.sql("delete from pending_import_item where import_id = ?").param(id).update();
        jdbcClient.sql("delete from pending_import where id = ?").param(id).update();
    }

    private List<ImportDraftItem> findItems(String importId) {
        return jdbcClient.sql("""
                select row_order, barcode, name, expected_qty, unit
                from pending_import_item
                where import_id = ?
                order by row_order
                """)
                .param(importId)
                .query((rs, rowNum) -> new ImportDraftItem(
                        rs.getInt("row_order"),
                        rs.getString("barcode"),
                        rs.getString("name"),
                        getNullableInt(rs, "expected_qty"),
                        rs.getString("unit")))
                .list();
    }

    private ImportDraft mapDraftHeader(ResultSet rs, int rowNum) throws SQLException {
        return new ImportDraft(
                rs.getString("id"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getString("error_message"),
                Instant.parse(rs.getString("created_at")),
                List.of()
        );
    }

    private Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
