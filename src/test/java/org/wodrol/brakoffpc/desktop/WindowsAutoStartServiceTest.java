package org.wodrol.brakoffpc.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldCreateUnicodeStartupScriptWithValidVbScriptQuoting() throws IOException {
        Path fakeLauncher = tempDir.resolve("Zażółć").resolve("BrakOffPC.exe");
        Files.createDirectories(fakeLauncher.getParent());
        Files.writeString(fakeLauncher, "");

        WindowsAutoStartService service = new WindowsAutoStartService(
                "Windows 11",
                Map.of("APPDATA", tempDir.toString()),
                tempDir.toString(),
                () -> Optional.of(fakeLauncher)
        );

        service.setEnabled(true);

        byte[] scriptBytes = Files.readAllBytes(startupScriptPath());
        assertEquals((byte) 0xFF, scriptBytes[0]);
        assertEquals((byte) 0xFE, scriptBytes[1]);

        String script = new String(Arrays.copyOfRange(scriptBytes, 2, scriptBytes.length), StandardCharsets.UTF_16LE);
        assertTrue(script.contains("launcherPath = \"" + fakeLauncher + "\""));
        assertTrue(script.contains("shell.Run Chr(34) & launcherPath & Chr(34), 0, False"));
        assertTrue(script.contains("fso.DeleteFile WScript.ScriptFullName, True"));
    }

    @Test
    void shouldRefreshExistingStartupScript() throws IOException {
        Path fakeLauncher = tempDir.resolve("BrakOffPC.exe");
        Files.writeString(fakeLauncher, "");
        Files.createDirectories(startupScriptPath().getParent());
        Files.writeString(startupScriptPath(), "old broken script");

        WindowsAutoStartService service = new WindowsAutoStartService(
                "Windows 11",
                Map.of("APPDATA", tempDir.toString()),
                tempDir.toString(),
                () -> Optional.of(fakeLauncher)
        );

        service.refreshIfEnabled();

        byte[] scriptBytes = Files.readAllBytes(startupScriptPath());
        String script = new String(Arrays.copyOfRange(scriptBytes, 2, scriptBytes.length), StandardCharsets.UTF_16LE);
        assertTrue(script.contains("launcherPath = \"" + fakeLauncher + "\""));
        assertFalse(script.contains("old broken script"));
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

    private Path startupScriptPath() {
        return tempDir.resolve("Microsoft")
                .resolve("Windows")
                .resolve("Start Menu")
                .resolve("Programs")
                .resolve("Startup")
                .resolve("BrakOffPC.vbs");
    }
}
