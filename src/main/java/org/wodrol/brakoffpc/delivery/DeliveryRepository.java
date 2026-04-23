package org.wodrol.brakoffpc.delivery;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class DeliveryRepository {

    private final JdbcClient jdbcClient;

    public DeliveryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(DeliveryRecord delivery) {
        jdbcClient.sql("""
                insert into delivery (id, source_file_name, status, created_at, activated_at)
                values (?, ?, ?, ?, ?)
                """)
                .params(
                        delivery.id(),
                        delivery.sourceFileName(),
                        delivery.status(),
                        delivery.createdAt().toString(),
                        delivery.activatedAt() != null ? delivery.activatedAt().toString() : null
                )
                .update();

        for (DeliveryItem item : delivery.items()) {
            jdbcClient.sql("""
                    insert into delivery_item (delivery_id, barcode, name, expected_qty)
                    values (?, ?, ?, ?)
                    """)
                    .params(delivery.id(), item.barcode(), item.name(), item.expectedQty())
                    .update();
        }
    }

    public int activateArchived(String id, Instant activatedAt) {
        return jdbcClient.sql("""
                update delivery
                set status = ?, activated_at = ?
                where id = ? and status <> ?
                """)
                .params(DeliveryStatus.ACTIVE, activatedAt.toString(), id, DeliveryStatus.ACTIVE)
                .update();
    }

    public void updateStatus(String id, String status) {
        jdbcClient.sql("""
                update delivery
                set status = ?
                where id = ?
                """)
                .params(status, id)
                .update();
    }

    public Optional<DeliveryRecord> findActive() {
        return findSingleByStatus(DeliveryStatus.ACTIVE);
    }

    public Optional<DeliveryRecord> findById(String id) {
        List<DeliveryRecord> rows = jdbcClient.sql("""
                select id, source_file_name, status, created_at, activated_at
                from delivery
                where id = ?
                limit 1
                """)
                .param(id)
                .query((rs, rowNum) -> new DeliveryRecord(
                        rs.getString("id"),
                        rs.getString("source_file_name"),
                        rs.getString("status"),
                        Instant.parse(rs.getString("created_at")),
                        parseNullableInstant(rs.getString("activated_at")),
                        List.of()
                ))
                .list();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRecord row = rows.getFirst();
        return Optional.of(new DeliveryRecord(
                row.id(),
                row.sourceFileName(),
                row.status(),
                row.createdAt(),
                row.activatedAt(),
                findItems(row.id())
        ));
    }

    public List<DeliveryArchiveSummary> findArchived() {
        return jdbcClient.sql("""
                select d.id, d.source_file_name, d.status, d.created_at, d.activated_at,
                       count(di.id) as item_count
                from delivery d
                left join delivery_item di on di.delivery_id = d.id
                where d.status <> ?
                group by d.id, d.source_file_name, d.status, d.created_at, d.activated_at
                order by coalesce(d.activated_at, d.created_at) desc
                """)
                .param(DeliveryStatus.ACTIVE)
                .query((rs, rowNum) -> new DeliveryArchiveSummary(
                        rs.getString("id"),
                        rs.getString("source_file_name"),
                        rs.getString("status"),
                        Instant.parse(rs.getString("created_at")),
                        parseNullableInstant(rs.getString("activated_at")),
                        rs.getInt("item_count")
                ))
                .list();
    }

    public int deleteArchivedOlderThan(Instant cutoff) {
        List<String> ids = jdbcClient.sql("""
                select id
                from delivery
                where status <> ?
                  and coalesce(activated_at, created_at) < ?
                """)
                .params(DeliveryStatus.ACTIVE, cutoff.toString())
                .query((rs, rowNum) -> rs.getString("id"))
                .list();
        deleteDeliveries(ids);
        return ids.size();
    }

    public void deleteDeliveries(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : new ArrayList<>(ids)) {
            jdbcClient.sql("delete from device_scan where delivery_id = ?")
                    .param(id)
                    .update();
            jdbcClient.sql("delete from delivery_item where delivery_id = ?")
                    .param(id)
                    .update();
            jdbcClient.sql("delete from delivery where id = ? and status <> ?")
                    .params(id, DeliveryStatus.ACTIVE)
                    .update();
        }
    }

    private Optional<DeliveryRecord> findSingleByStatus(String status) {
        List<DeliveryRecord> rows = jdbcClient.sql("""
                select id, source_file_name, status, created_at, activated_at
                from delivery
                where status = ?
                order by activated_at desc
                limit 1
                """)
                .param(status)
                .query((rs, rowNum) -> new DeliveryRecord(
                        rs.getString("id"),
                        rs.getString("source_file_name"),
                        rs.getString("status"),
                        Instant.parse(rs.getString("created_at")),
                        parseNullableInstant(rs.getString("activated_at")),
                        List.of()
                ))
                .list();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        DeliveryRecord row = rows.getFirst();
        return Optional.of(new DeliveryRecord(
                row.id(),
                row.sourceFileName(),
                row.status(),
                row.createdAt(),
                row.activatedAt(),
                findItems(row.id())
        ));
    }

    public List<DeliveryItem> findItems(String deliveryId) {
        return jdbcClient.sql("""
                select barcode, name, expected_qty
                from delivery_item
                where delivery_id = ?
                order by barcode
                """)
                .param(deliveryId)
                .query((rs, rowNum) -> new DeliveryItem(
                        deliveryId,
                        rs.getString("barcode"),
                        rs.getString("name"),
                        rs.getInt("expected_qty")))
                .list();
    }

    public void replaceItems(String deliveryId, List<DeliveryItem> items) {
        jdbcClient.sql("delete from delivery_item where delivery_id = ?")
                .param(deliveryId)
                .update();

        for (DeliveryItem item : items) {
            jdbcClient.sql("""
                    insert into delivery_item (delivery_id, barcode, name, expected_qty)
                    values (?, ?, ?, ?)
                    """)
                    .params(deliveryId, item.barcode(), item.name(), item.expectedQty())
                    .update();
        }
    }

    public List<DeviceScanState> findScans(String deliveryId) {
        return jdbcClient.sql("""
                select device_id, barcode, quantity, revision, updated_at, device_name, item_name
                from device_scan
                where delivery_id = ?
                order by device_id, barcode
                """)
                .param(deliveryId)
                .query((rs, rowNum) -> new DeviceScanState(
                        rs.getString("device_id"),
                        rs.getString("device_name"),
                        rs.getString("barcode"),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getLong("revision"),
                        Instant.parse(rs.getString("updated_at"))))
                .list();
    }

    public Optional<DeviceScanState> findScan(String deliveryId, String deviceId, String barcode) {
        List<DeviceScanState> states = jdbcClient.sql("""
                select device_id, barcode, quantity, revision, updated_at, device_name, item_name
                from device_scan
                where delivery_id = ? and device_id = ? and barcode = ?
                limit 1
                """)
                .params(deliveryId, deviceId, barcode)
                .query((rs, rowNum) -> new DeviceScanState(
                        rs.getString("device_id"),
                        rs.getString("device_name"),
                        rs.getString("barcode"),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getLong("revision"),
                        Instant.parse(rs.getString("updated_at"))))
                .list();
        return states.stream().findFirst();
    }

    public void upsertScan(String deliveryId, DeviceScanState scanState) {
        Optional<DeviceScanState> existing = findScan(deliveryId, scanState.deviceId(), scanState.barcode());
        if (existing.isPresent()) {
            jdbcClient.sql("""
                    update device_scan
                    set quantity = ?, revision = ?, updated_at = ?, device_name = ?, item_name = ?
                    where delivery_id = ? and device_id = ? and barcode = ?
                    """)
                    .params(
                            scanState.quantity(),
                            scanState.revision(),
                            scanState.updatedAt().toString(),
                            scanState.deviceName(),
                            scanState.itemName(),
                            deliveryId,
                            scanState.deviceId(),
                            scanState.barcode())
                    .update();
            return;
        }

        jdbcClient.sql("""
                insert into device_scan (delivery_id, device_id, device_name, barcode, item_name, quantity, revision, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(
                        deliveryId,
                        scanState.deviceId(),
                        scanState.deviceName(),
                        scanState.barcode(),
                        scanState.itemName(),
                        scanState.quantity(),
                        scanState.revision(),
                        scanState.updatedAt().toString())
                .update();
    }

    public void replaceScans(String deliveryId, List<DeviceScanState> scans) {
        jdbcClient.sql("delete from device_scan where delivery_id = ?")
                .param(deliveryId)
                .update();

        for (DeviceScanState scan : scans) {
            jdbcClient.sql("""
                    insert into device_scan (delivery_id, device_id, device_name, barcode, item_name, quantity, revision, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(
                            deliveryId,
                            scan.deviceId(),
                            scan.deviceName(),
                            scan.barcode(),
                            scan.itemName(),
                            scan.quantity(),
                            scan.revision(),
                            scan.updatedAt().toString())
                    .update();
        }
    }

    private Instant parseNullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
