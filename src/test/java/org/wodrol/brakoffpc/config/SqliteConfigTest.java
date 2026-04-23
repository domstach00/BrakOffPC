package org.wodrol.brakoffpc.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteConfigTest {

    @Test
    void prefersExplicitConfiguredPath() {
        Path databasePath = SqliteConfig.resolveDatabasePath(
                "/tmp/brakoffpc/custom.db",
                Path.of("brakoffpc.db"),
                "Linux",
                Map.of(),
                "/home/test"
        );

        assertEquals(Path.of("/tmp/brakoffpc/custom.db").toAbsolutePath().normalize(), databasePath);
    }

    @Test
    void usesWindowsLocalAppDataByDefault() {
        Path databasePath = SqliteConfig.resolveDatabasePath(
                "",
                Path.of("missing-legacy.db"),
                "Windows 11",
                Map.of("LOCALAPPDATA", "C:\\Users\\dominik\\AppData\\Local"),
                "C:\\Users\\dominik"
        );

        assertEquals(
                Path.of("C:\\Users\\dominik\\AppData\\Local", "BrakOffPC", "brakoffpc.db").toAbsolutePath().normalize(),
                databasePath
        );
    }

    @Test
    void usesLinuxXdgDataHomeByDefault() {
        Path databasePath = SqliteConfig.resolveDatabasePath(
                "",
                Path.of("missing-legacy.db"),
                "Linux",
                Map.of("XDG_DATA_HOME", "/home/dominik/.local/share-custom"),
                "/home/dominik"
        );

        assertEquals(
                Path.of("/home/dominik/.local/share-custom", "brakoffpc", "brakoffpc.db").toAbsolutePath().normalize(),
                databasePath
        );
    }

    @Test
    void usesLinuxUserHomeFallbackWhenXdgIsMissing() {
        Path databasePath = SqliteConfig.resolveDatabasePath(
                "",
                Path.of("missing-legacy.db"),
                "Linux",
                Map.of(),
                "/home/dominik"
        );

        assertEquals(
                Path.of("/home/dominik/.local/share/brakoffpc/brakoffpc.db").toAbsolutePath().normalize(),
                databasePath
        );
    }
}
