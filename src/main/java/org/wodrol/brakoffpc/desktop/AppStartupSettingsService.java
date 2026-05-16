package org.wodrol.brakoffpc.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.wodrol.brakoffpc.config.AppPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

@Service
public class AppStartupSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppStartupSettingsService.class);
    private static final String OPEN_BROWSER_ON_STARTUP_KEY = "openBrowserOnStartup";
    private final Path settingsFile;

    public AppStartupSettingsService() {
        this(defaultSettingsFile(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home")));
    }

    AppStartupSettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public synchronized AppStartupSettings load() {
        if (!Files.exists(settingsFile)) {
            return AppStartupSettings.defaults();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(settingsFile)) {
            properties.load(inputStream);
            return new AppStartupSettings(parseBoolean(properties.getProperty(OPEN_BROWSER_ON_STARTUP_KEY), true));
        } catch (IOException exception) {
            log.warn("Nie udalo sie odczytac ustawien startowych z pliku {}.", settingsFile, exception);
            return AppStartupSettings.defaults();
        }
    }

    public synchronized void save(AppStartupSettings settings) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(OPEN_BROWSER_ON_STARTUP_KEY, String.valueOf(settings.openBrowserOnStartup()));

        Path parentDirectory = settingsFile.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
            properties.store(outputStream, "BrakOff PC startup settings");
        }
    }

    static Path defaultSettingsFile(String osName, Map<String, String> environment, String userHome) {
        return AppPaths.resolveAppDataDirectory(osName, environment, userHome).resolve("startup-settings.properties");
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
