package org.wodrol.brakoffpc.delivery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanUpdateRequest {

    private String deliveryId;
    @NotBlank
    private String deviceId;
    private String deviceName;
    @NotBlank
    private String barcode;
    private String name;
    @Min(0)
    private int quantity;
    private boolean fromDelivery;
    @Min(0)
    private long revision;
    @NotNull
    private Instant updatedAt;

    private static final DateTimeFormatter MOBILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isFromDelivery() {
        return fromDelivery;
    }

    public void setFromDelivery(boolean fromDelivery) {
        this.fromDelivery = fromDelivery;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonSetter("updatedAt")
    public void setUpdatedAtRaw(String updatedAt) {
        if (updatedAt == null || updatedAt.isBlank()) {
            this.updatedAt = null;
            return;
        }

        try {
            this.updatedAt = OffsetDateTime.parse(updatedAt, MOBILE_DATE_FORMAT).toInstant();
        } catch (DateTimeParseException mobileFormatException) {
            this.updatedAt = OffsetDateTime.parse(updatedAt).toInstant();
        }
    }
}
