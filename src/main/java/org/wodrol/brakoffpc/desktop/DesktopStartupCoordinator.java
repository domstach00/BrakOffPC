package org.wodrol.brakoffpc.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DesktopStartupCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DesktopStartupCoordinator.class);
    private final AppStartupSettingsService appStartupSettingsService;
    private final WindowsAutoStartService windowsAutoStartService;
    private final ApplicationContext applicationContext;

    public DesktopStartupCoordinator(
            AppStartupSettingsService appStartupSettingsService,
            WindowsAutoStartService windowsAutoStartService,
            ApplicationContext applicationContext
    ) {
        this.appStartupSettingsService = appStartupSettingsService;
        this.windowsAutoStartService = windowsAutoStartService;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserAfterStartup() {
        if (!DesktopLauncherSupport.isPackagedLaunch()) {
            return;
        }

        refreshWindowsAutoStartScript();

        if (!appStartupSettingsService.load().openBrowserOnStartup()) {
            return;
        }

        if (!(applicationContext instanceof WebServerApplicationContext webServerApplicationContext)) {
            return;
        }

        DesktopLauncherSupport.openInBrowser(
                "http://127.0.0.1:" + webServerApplicationContext.getWebServer().getPort() + "/",
                System.getProperty("os.name")
        );
    }

    private void refreshWindowsAutoStartScript() {
        try {
            windowsAutoStartService.refreshIfEnabled();
        } catch (IOException | IllegalStateException exception) {
            log.warn("Nie udalo sie odswiezyc skryptu autostartu Windows: {}", exception.getMessage());
        }
    }
}
