package org.wodrol.brakoffpc.desktop;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DesktopLauncherSupport {

    private DesktopLauncherSupport() {
    }

    public static boolean tryOpenExistingInstance(String[] args) {
        if (!isPackagedLaunch()) {
            return false;
        }

        int serverPort = resolveServerPort(args, System.getProperties(), System.getenv());
        String healthUrl = "http://127.0.0.1:" + serverPort + "/api/health";
        if (!isHealthy(healthUrl)) {
            return false;
        }

        openInBrowser("http://127.0.0.1:" + serverPort + "/", System.getProperty("os.name"));
        return true;
    }

    public static boolean isPackagedLaunch() {
        if (detectLauncherPath().isPresent()) {
            return true;
        }

        return System.getProperty("jpackage.app-path") != null;
    }

    public static Optional<Path> detectLauncherPath() {
        String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            Path launcherPath = Path.of(jpackageAppPath).toAbsolutePath().normalize();
            if (isSupportedLauncherPath(launcherPath)) {
                return Optional.of(launcherPath);
            }
        }

        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(DesktopLauncherSupport::isSupportedLauncherPath);
    }

    public static void openInBrowser(String url, String osName) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(browserOpenCommand(url, osName));
            processBuilder.redirectErrorStream(true);
            processBuilder.start();
        } catch (IOException ignored) {
        }
    }

    public static int resolveServerPort(String[] args, Map<Object, Object> systemProperties, Map<String, String> environment) {
        Object systemPropertyPort = systemProperties.get("server.port");
        if (systemPropertyPort instanceof String value && !value.isBlank()) {
            return parsePort(value, 8080);
        }

        String environmentPort = environment.get("SERVER_PORT");
        if (environmentPort != null && !environmentPort.isBlank()) {
            return parsePort(environmentPort, 8080);
        }

        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--server.port=")) {
                    return parsePort(arg.substring("--server.port=".length()), 8080);
                }
            }
        }

        return 8080;
    }

    private static String[] browserOpenCommand(String url, String osName) {
        String normalizedOsName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalizedOsName.contains("win")) {
            return new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
        }
        if (normalizedOsName.contains("mac")) {
            return new String[]{"open", url};
        }
        return new String[]{"xdg-open", url};
    }

    private static boolean isHealthy(String healthUrl) {
        for (int attempt = 0; attempt < 6; attempt++) {
            if (tryHealthRequest(healthUrl)) {
                return true;
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private static boolean tryHealthRequest(String healthUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(healthUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(500);
            connection.setReadTimeout(500);
            connection.connect();
            if (connection.getResponseCode() != 200) {
                return false;
            }

            String responseBody = new String(connection.getInputStream().readAllBytes());
            return responseBody.contains("\"status\"") && responseBody.contains("\"ok\"");
        } catch (IOException exception) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isSupportedLauncherPath(Path launcherPath) {
        String fileName = launcherPath.getFileName() == null ? "" : launcherPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".exe") && !fileName.equals("java.exe") && !fileName.equals("javaw.exe");
    }

    private static int parsePort(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
