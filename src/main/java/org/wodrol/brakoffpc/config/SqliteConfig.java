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

@Configuration
public class SqliteConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfig.class);

    @Bean
    DataSource dataSource(@Value("${app.sqlite.path:}") String configuredPath) throws IOException {
        Path databasePath = resolveDatabasePath(configuredPath);
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

    private Path resolveDatabasePath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath).toAbsolutePath().normalize();
        }

        Path legacyDatabase = Path.of("brakoffpc.db").toAbsolutePath().normalize();
        if (Files.exists(legacyDatabase)) {
            return legacyDatabase;
        }

        return Path.of(System.getProperty("user.home"), ".brakoffpc", "brakoffpc.db")
                .toAbsolutePath()
                .normalize();
    }
}
