package org.wodrol.brakoffpc.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class AppPaths {

    private AppPaths() {
    }

    public static Path resolveAppDataDirectory(String osName, Map<String, String> environment, String userHome) {
        if (isWindows(osName)) {
            Path localAppDataPath = pathFromEnv(environment, "LOCALAPPDATA");
            if (localAppDataPath != null) {
                return localAppDataPath.resolve("BrakOffPC").toAbsolutePath().normalize();
            }

            Path appDataPath = pathFromEnv(environment, "APPDATA");
            if (appDataPath != null) {
                return appDataPath.resolve("BrakOffPC").toAbsolutePath().normalize();
            }

            return Path.of(userHome, "AppData", "Local", "BrakOffPC")
                    .toAbsolutePath()
                    .normalize();
        }

        Path xdgDataHomePath = pathFromEnv(environment, "XDG_DATA_HOME");
        if (xdgDataHomePath != null) {
            return xdgDataHomePath.resolve("brakoffpc").toAbsolutePath().normalize();
        }

        return Path.of(userHome, ".local", "share", "brakoffpc")
                .toAbsolutePath()
                .normalize();
    }

    public static Path resolveWindowsStartupDirectory(Map<String, String> environment, String userHome) {
        Path appDataPath = pathFromEnv(environment, "APPDATA");
        if (appDataPath != null) {
            return appDataPath.resolve("Microsoft")
                    .resolve("Windows")
                    .resolve("Start Menu")
                    .resolve("Programs")
                    .resolve("Startup")
                    .toAbsolutePath()
                    .normalize();
        }

        return Path.of(userHome, "AppData", "Roaming", "Microsoft", "Windows", "Start Menu", "Programs", "Startup")
                .toAbsolutePath()
                .normalize();
    }

    public static boolean isWindows(String osName) {
        String normalizedOsName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        return normalizedOsName.contains("win");
    }

    private static Path pathFromEnv(Map<String, String> environment, String variableName) {
        String value = environment.get(variableName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value);
    }
}
