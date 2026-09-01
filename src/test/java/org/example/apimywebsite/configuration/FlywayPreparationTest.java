package org.example.apimywebsite.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H1-B: proves the final Flyway/ddl-auto configuration (dependency + migration files + config)
 * is internally consistent - no Spring context, no database connection. Flyway itself is never
 * invoked here; these are plain classpath/Properties checks, matching this project's existing
 * no-DB test pattern (SqlLoggingPropertiesTest). Production has already been baselined and
 * migrated through V3 (verified separately, against the real schema, in prior passes); this
 * class now asserts the RESOLVED end state (spring.flyway.enabled=true,
 * ddl-auto=validate) rather than the earlier "deliberately inert" preparation state.
 */
class FlywayPreparationTest {

    private static final List<String> MIGRATION_FILES = List.of(
            "db/migration/V2__add_missing_not_null_constraints.sql",
            "db/migration/V3__add_unique_user_name_constraint.sql"
    );

    private Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties must be on the test classpath");
            properties.load(in);
        }
        return properties;
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " must be on the classpath (default Flyway location)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void flywayIsNowEnabled_theRolloutSequenceHasBeenCompleted() throws IOException {
        Properties properties = loadApplicationProperties();
        assertEquals("true", properties.getProperty("spring.flyway.enabled"),
                "Flyway is the active schema-management authority now that baseline+migrate have both run and been verified against production");
    }

    @Test
    void baselineOnMigrateStaysExplicitlyDisabled_neverAnImplicitStartupSideEffect() throws IOException {
        Properties properties = loadApplicationProperties();
        assertEquals("false", properties.getProperty("spring.flyway.baseline-on-migrate"),
                "must stay false even now that production has been baselined once - a future environment with a non-empty, not-yet-tracked schema must never be auto-baselined as a side effect of booting");
    }

    @Test
    void ddlAutoIsNowValidate() throws IOException {
        Properties properties = loadApplicationProperties();
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"),
                "ddl-auto flips to validate only after V2/V3 were actually applied against production and independently verified - see the isolated @DataJpaTest validation performed for H1-B's final pass");
    }

    @Test
    void noExplicitFlywayDatasourceOverride_soFlywayReusesTheSharedEnvBasedDatasource() throws IOException {
        Properties properties = loadApplicationProperties();
        assertNull(properties.getProperty("spring.flyway.url"),
                "no separate Flyway datasource - it must reuse spring.datasource.* (${DB_URL} etc.)");
        assertNull(properties.getProperty("spring.flyway.user"));
        assertNull(properties.getProperty("spring.flyway.password"));
    }

    @Test
    void bothMigrationFilesExistOnTheDefaultFlywayClasspathLocation_andAreNonEmpty() throws IOException {
        for (String path : MIGRATION_FILES) {
            String content = readClasspathResource(path);
            assertFalse(content.isBlank(), path + " must not be empty");
        }
    }

    @Test
    void migrationFilenamesFollowFlywayNamingConvention_startingAfterTheDefaultBaselineVersion() {
        // Default spring.flyway.baseline-version is 1 - the first real migration must be >= 2,
        // otherwise `flyway baseline` would treat it as already-applied and silently skip it.
        for (String path : MIGRATION_FILES) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            assertTrue(filename.matches("V\\d+__\\w+\\.sql"),
                    filename + " must match Flyway's V<version>__<description>.sql convention");
            int version = Integer.parseInt(filename.substring(1, filename.indexOf("__")));
            assertTrue(version >= 2, filename + " must be versioned above the default baseline version (1)");
        }
    }

    // Strips SQL line-comments (-- ...) before keyword-scanning, so prose in the explanatory
    // header comments (e.g. "would silently drop it") can't produce a false positive.
    private String stripSqlComments(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            int idx = line.indexOf("--");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    @Test
    void migrationsContainOnlyAdditiveAlterStatements_noDropCreateTruncateOrDataStatements() throws IOException {
        List<String> forbidden = List.of("drop ", "create table", "truncate", "delete from", "insert into", "update ");
        for (String path : MIGRATION_FILES) {
            String lower = stripSqlComments(readClasspathResource(path)).toLowerCase(Locale.ROOT);
            for (String keyword : forbidden) {
                assertFalse(lower.contains(keyword),
                        path + " must not contain a destructive/data-changing statement (found: " + keyword.trim() + ")");
            }
            assertTrue(lower.contains("alter table"), path + " is expected to be an ALTER TABLE-only migration");
        }
    }
}
