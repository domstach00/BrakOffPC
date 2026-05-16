package org.wodrol.brakoffpc.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStartupSettingsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUseDefaultSettingsWhenFileDoesNotExist() {
        AppStartupSettingsService service = new AppStartupSettingsService(tempDir.resolve("startup-settings.properties"));

        AppStartupSettings settings = service.load();

        assertTrue(settings.openBrowserOnStartup());
    }

    @Test
    void shouldSaveAndLoadSettings() throws IOException {
        AppStartupSettingsService service = new AppStartupSettingsService(tempDir.resolve("startup-settings.properties"));

        service.save(new AppStartupSettings(false));
        AppStartupSettings loaded = service.load();

        assertFalse(loaded.openBrowserOnStartup());
    }
}
