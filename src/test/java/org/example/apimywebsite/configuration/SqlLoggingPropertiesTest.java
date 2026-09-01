package org.example.apimywebsite.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * H1-A fix: proves the repository's checked-in defaults for SQL statement/bind-parameter
 * logging are safe (off) when no operator has explicitly opted in via environment variable.
 * Plain Properties load + Spring's own ${VAR:default} placeholder resolution (no Spring context,
 * no database) - resolving the *default* is what matters here, not just that some value string
 * is present, since the raw file only ever contains "${HIBERNATE_SQL_LOG_LEVEL:WARN}" etc.
 *
 * H1-A itself never touched spring.jpa.hibernate.ddl-auto - that was H1-B, resolved separately
 * and later (production baselined/migrated via Flyway, then ddl-auto flipped to validate and
 * independently verified against the real schema).
 */
class SqlLoggingPropertiesTest {

    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER =
            new PropertyPlaceholderHelper("${", "}", ":", true);

    private Properties loadApplicationProperties() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(in, "application.properties must be on the test classpath");
            properties.load(in);
        }
        return properties;
    }

    // Resolves ${VAR:default} exactly as Spring would when VAR is unset in the environment.
    private String resolveWithNoEnvironmentOverride(String rawPlaceholderValue) {
        return PLACEHOLDER_HELPER.replacePlaceholders(rawPlaceholderValue, new Properties());
    }

    private void assertPropertyDefaultsTo(Properties properties, String key, String expectedDefault) {
        String raw = properties.getProperty(key);
        assertNotNull(raw, key + " must be explicitly configured");
        assertEquals(expectedDefault, resolveWithNoEnvironmentOverride(raw),
                key + " must default to " + expectedDefault + " when no environment override is set");
    }

    @Test
    void showSql_defaultsToFalse() throws Exception {
        assertPropertyDefaultsTo(loadApplicationProperties(), "spring.jpa.show-sql", "false");
    }

    @Test
    void hibernateSqlStatementLogging_defaultsToWarn_notDebug() throws Exception {
        assertPropertyDefaultsTo(loadApplicationProperties(), "logging.level.org.hibernate.SQL", "WARN");
    }

    @Test
    void hibernateBindParameterLogging_currentHibernate6LoggerName_defaultsToWarn_notTrace() throws Exception {
        assertPropertyDefaultsTo(loadApplicationProperties(),
                "logging.level.org.hibernate.orm.jdbc.bind", "WARN");
    }

    @Test
    void hibernateBindParameterLogging_legacyLoggerAlreadyInProject_defaultsToWarn_notTrace() throws Exception {
        assertPropertyDefaultsTo(loadApplicationProperties(),
                "logging.level.org.hibernate.type.descriptor.sql", "WARN");
    }

    @Test
    void hibernateBasicBinderLogging_defaultsToWarn_notTrace() throws Exception {
        assertPropertyDefaultsTo(loadApplicationProperties(),
                "logging.level.org.hibernate.type.descriptor.sql.BasicBinder", "WARN");
    }

    @Test
    void explicitOperationalOptIn_stillPossibleViaEnvironmentVariable() throws Exception {
        // Proves the escape hatch exists and actually resolves to the opted-in value - not
        // just that the default is safe, but that raising it remains a real, explicit choice.
        Properties properties = loadApplicationProperties();
        String raw = properties.getProperty("logging.level.org.hibernate.SQL");
        Properties override = new Properties();
        override.setProperty("HIBERNATE_SQL_LOG_LEVEL", "TRACE");
        assertEquals("TRACE", PLACEHOLDER_HELPER.replacePlaceholders(raw, override));
    }

    @Test
    void ddlAutoIsValidate_H1BNowResolved() throws Exception {
        // H1-A didn't touch ddl-auto; H1-B later did, once Flyway's V2/V3 migrations were
        // applied and independently verified against production (see FlywayPreparationTest and
        // BACKEND_DEEP_AUDIT.md's [H1-B] section) - this assertion tracks that final state.
        Properties properties = loadApplicationProperties();
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
