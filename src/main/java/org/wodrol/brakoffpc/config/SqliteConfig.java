package org.wodrol.brakoffpc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;

@Configuration
public class SqliteConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfig.class);

    @Bean
    DataSource dataSource(@Value("${app.sqlite.path:}") String configuredPath) throws IOException {
        Path databasePath = resolveDatabasePath(
                configuredPath,
                Path.of("brakoffpc.db"),
                System.getProperty("os.name"),
                System.getenv(),
                System.getProperty("user.home")
        );
        Path parentDirectory = databasePath.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());
        log.info("SQLite database path: {}", databasePath.toAbsolutePath());
        return dataSource;
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    static Path resolveDatabasePath(
            String configuredPath,
            Path legacyDatabase,
            String osName,
            Map<String, String> environment,
            String userHome
    ) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }

        Path normalizedLegacyDatabase = legacyDatabase.toAbsolutePath().normalize();
        if (Files.exists(normalizedLegacyDatabase)) {
            return normalizedLegacyDatabase;
        }

        return defaultDatabasePath(osName, environment, userHome);
    }

    private static Path defaultDatabasePath(String osName, Map<String, String> environment, String userHome) {
        String normalizedOsName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalizedOsName.contains("win")) {
            Path localAppDataPath = pathFromEnv(environment, "LOCALAPPDATA");
            if (localAppDataPath != null) {
                return localAppDataPath.resolve("BrakOffPC").resolve("brakoffpc.db").toAbsolutePath().normalize();
            }

            Path appDataPath = pathFromEnv(environment, "APPDATA");
            if (appDataPath != null) {
                return appDataPath.resolve("BrakOffPC").resolve("brakoffpc.db").toAbsolutePath().normalize();
            }

            return Path.of(userHome, "AppData", "Local", "BrakOffPC", "brakoffpc.db")
                    .toAbsolutePath()
                    .normalize();
        }

        Path xdgDataHomePath = pathFromEnv(environment, "XDG_DATA_HOME");
        if (xdgDataHomePath != null) {
            return xdgDataHomePath.resolve("brakoffpc").resolve("brakoffpc.db").toAbsolutePath().normalize();
        }

        return Path.of(userHome, ".local", "share", "brakoffpc", "brakoffpc.db")
                .toAbsolutePath()
                .normalize();
    }

    private static Path pathFromEnv(Map<String, String> environment, String variableName) {
        String value = environment.get(variableName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value);
    }
}
