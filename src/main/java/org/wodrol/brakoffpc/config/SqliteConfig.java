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
        return AppPaths.resolveAppDataDirectory(osName, environment, userHome).resolve("brakoffpc.db");
    }
}
