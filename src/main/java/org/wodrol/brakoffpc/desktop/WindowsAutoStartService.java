package org.wodrol.brakoffpc.desktop;

import org.springframework.stereotype.Service;
import org.wodrol.brakoffpc.config.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class WindowsAutoStartService {

    private static final String STARTUP_SCRIPT_FILE_NAME = "BrakOffPC.vbs";
    private static final byte[] UTF_16_LE_BOM = new byte[]{(byte) 0xFF, (byte) 0xFE};
    private final String osName;
    private final Map<String, String> environment;
    private final String userHome;
    private final Supplier<Optional<Path>> launcherPathSupplier;

    public WindowsAutoStartService() {
        this(System.getProperty("os.name"), System.getenv(), System.getProperty("user.home"), DesktopLauncherSupport::detectLauncherPath);
    }

    WindowsAutoStartService(
            String osName,
            Map<String, String> environment,
            String userHome,
            Supplier<Optional<Path>> launcherPathSupplier
    ) {
        this.osName = osName;
        this.environment = environment;
        this.userHome = userHome;
        this.launcherPathSupplier = launcherPathSupplier;
    }

    public boolean isSupported() {
        return AppPaths.isWindows(osName);
    }

    public boolean isConfigurable() {
        return isSupported() && launcherPathSupplier.get().isPresent();
    }

    public boolean isEnabled() {
        return isSupported() && Files.exists(startupScriptPath());
    }

    public String availabilityHint() {
        if (!isSupported()) {
            return "Autostart jest dostępny po instalacji wersji Windows.";
        }
        if (!isConfigurable()) {
            return "Autostart włączy się po uruchomieniu z zainstalowanej aplikacji Windows.";
        }
        return "Aplikacja uruchomi się razem z logowaniem użytkownika do Windows.";
    }

    public void setEnabled(boolean enabled) throws IOException {
        if (!isSupported()) {
            if (enabled) {
                throw new IllegalStateException("Autostart jest dostępny tylko w zainstalowanej wersji Windows.");
            }
            return;
        }

        if (!enabled) {
            Files.deleteIfExists(startupScriptPath());
            return;
        }

        Path launcherPath = launcherPathSupplier.get()
                .orElseThrow(() -> new IllegalStateException("Autostart włączysz po uruchomieniu z zainstalowanej aplikacji Windows."));

        Path startupScriptPath = startupScriptPath();
        Files.createDirectories(startupScriptPath.getParent());
        Files.write(startupScriptPath, startupScriptBytes(launcherPath));
    }

    public void refreshIfEnabled() throws IOException {
        if (isEnabled()) {
            setEnabled(true);
        }
    }

    private Path startupScriptPath() {
        return AppPaths.resolveWindowsStartupDirectory(environment, userHome).resolve(STARTUP_SCRIPT_FILE_NAME);
    }

    private String startupScriptContent(Path launcherPath) {
        return """
                On Error Resume Next
                Set fso = CreateObject("Scripting.FileSystemObject")
                launcherPath = "%s"
                If fso.FileExists(launcherPath) Then
                    Set shell = CreateObject("WScript.Shell")
                    shell.Run Chr(34) & launcherPath & Chr(34), 0, False
                Else
                    fso.DeleteFile WScript.ScriptFullName, True
                End If
                """.formatted(vbScriptStringValue(launcherPath.toString()));
    }

    private byte[] startupScriptBytes(Path launcherPath) {
        byte[] content = startupScriptContent(launcherPath).getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[UTF_16_LE_BOM.length + content.length];
        System.arraycopy(UTF_16_LE_BOM, 0, bytes, 0, UTF_16_LE_BOM.length);
        System.arraycopy(content, 0, bytes, UTF_16_LE_BOM.length, content.length);
        return bytes;
    }

    private String vbScriptStringValue(String value) {
        return value.replace("\"", "\"\"");
    }
}
