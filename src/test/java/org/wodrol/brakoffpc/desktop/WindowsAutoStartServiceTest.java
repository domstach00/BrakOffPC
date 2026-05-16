package org.wodrol.brakoffpc.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsAutoStartServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateAndRemoveStartupScript() throws IOException {
        Path fakeLauncher = tempDir.resolve("BrakOffPC.exe");
        Files.writeString(fakeLauncher, "");

        WindowsAutoStartService service = new WindowsAutoStartService(
                "Windows 11",
                Map.of("APPDATA", tempDir.toString()),
                tempDir.toString(),
                () -> Optional.of(fakeLauncher)
        );

        service.setEnabled(true);

        assertTrue(service.isEnabled());

        service.setEnabled(false);

        assertFalse(service.isEnabled());
    }

    @Test
    void shouldRejectEnablingAutostartWhenLauncherIsUnknown() {
        WindowsAutoStartService service = new WindowsAutoStartService(
                "Windows 11",
                Map.of("APPDATA", tempDir.toString()),
                tempDir.toString(),
                Optional::<Path>empty
        );

        assertThrows(IllegalStateException.class, () -> service.setEnabled(true));
    }
}
