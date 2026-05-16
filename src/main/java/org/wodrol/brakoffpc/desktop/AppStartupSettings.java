package org.wodrol.brakoffpc.desktop;

public record AppStartupSettings(boolean openBrowserOnStartup) {

    public static AppStartupSettings defaults() {
        return new AppStartupSettings(true);
    }
}
