package org.wodrol.brakoffpc.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A separate, immutable operation; retries must use the same commentId and payload. */
public record ItemCommentRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String commentId,
        @NotBlank @Size(max = 200) String deviceId,
        @Size(max = 200) String deviceName,
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 100) @Pattern(regexp = "[0-9]+") String suggestedBarcode,
        @Size(max = 500) String suggestedName
) {
}
