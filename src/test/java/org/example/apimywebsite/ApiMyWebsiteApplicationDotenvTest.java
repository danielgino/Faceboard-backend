package org.example.apimywebsite;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Startup-configuration tests for the .env loading in ApiMyWebsiteApplication.main()
 * (item 3 of the H3 follow-up): the app must not crash before Spring even starts when
 * no .env file exists (e.g. Render, where secrets are supplied as real platform
 * environment variables), while still working normally when a local .env is present.
 * No secret values are printed or asserted on here — only presence/absence and
 * exception behavior.
 */
class ApiMyWebsiteApplicationDotenvTest {

    @Test
    void plainDotenvLoad_throwsWhenNoEnvFileExists(@TempDir Path emptyDir) {
        // Documents the exact pre-fix bug: the original plain Dotenv.load() (no
        // .ignoreIfMissing()) throws when the working directory has no .env file —
        // exactly the situation on a fresh Render deployment.
        assertThrows(DotenvException.class, () ->
                Dotenv.configure().directory(emptyDir.toString()).load());
    }

    @Test
    void ignoreIfMissingDotenvLoad_doesNotThrow_whenNoEnvFileExists(@TempDir Path emptyDir) {
        // The fix applied in ApiMyWebsiteApplication.main().
        Dotenv dotenv = assertDoesNotThrow(() ->
                Dotenv.configure().directory(emptyDir.toString()).ignoreIfMissing().load());

        assertNotNull(dotenv);
    }

    @Test
    void dotenvGet_forAKeyPresentOnlyAsARealEnvironmentVariable_stillResolves(@TempDir Path emptyDir) {
        // Simulates Render: no .env file, but the platform has injected a real OS
        // environment variable. Dotenv.get() checks System.getenv() first internally,
        // so this must resolve correctly even with an empty/ignored .env.
        Dotenv dotenv = Dotenv.configure().directory(emptyDir.toString()).ignoreIfMissing().load();

        // PATH is guaranteed to exist as a real OS environment variable in any environment
        // this test runs in, and contains no application secret.
        assertNotNull(System.getenv("PATH"), "test precondition: PATH must be set in this environment");
        assertEquals(System.getenv("PATH"), dotenv.get("PATH"));
    }

    @Test
    void dotenvGet_forAKeyAbsentFromBothSources_returnsNullRatherThanThrowing(@TempDir Path emptyDir) {
        Dotenv dotenv = Dotenv.configure().directory(emptyDir.toString()).ignoreIfMissing().load();

        assertNull(dotenv.get("SOME_KEY_THAT_DEFINITELY_DOES_NOT_EXIST_ANYWHERE_12345"));
    }

    @Test
    void settingSystemPropertyWithANullValue_throwsNullPointerException() {
        // Documents exactly why a null-guard is required: System.setProperty(key, null)
        // throws NPE, which is what would otherwise crash main() (before Spring starts)
        // for any key missing from both the .env file and the real environment, even
        // after fixing the missing-file case above.
        assertThrows(NullPointerException.class, () -> System.setProperty("SOME_TEST_KEY", null));
    }

    @Test
    void setPropertyIfPresent_skipsAbsentKeys_withoutThrowing(@TempDir Path emptyDir) throws Exception {
        Dotenv dotenv = Dotenv.configure().directory(emptyDir.toString()).ignoreIfMissing().load();
        Method setPropertyIfPresent = ApiMyWebsiteApplication.class
                .getDeclaredMethod("setPropertyIfPresent", Dotenv.class, String.class);
        setPropertyIfPresent.setAccessible(true);

        String missingKey = "SOME_KEY_THAT_DEFINITELY_DOES_NOT_EXIST_ANYWHERE_12345";
        System.clearProperty(missingKey);

        assertDoesNotThrow(() -> setPropertyIfPresent.invoke(null, dotenv, missingKey));

        assertNull(System.getProperty(missingKey));
    }

    @Test
    void setPropertyIfPresent_setsThePropertyWhenTheKeyIsPresent(@TempDir Path emptyDir) throws Exception {
        Dotenv dotenv = Dotenv.configure().directory(emptyDir.toString()).ignoreIfMissing().load();
        Method setPropertyIfPresent = ApiMyWebsiteApplication.class
                .getDeclaredMethod("setPropertyIfPresent", Dotenv.class, String.class);
        setPropertyIfPresent.setAccessible(true);

        setPropertyIfPresent.invoke(null, dotenv, "PATH");

        assertEquals(System.getenv("PATH"), System.getProperty("PATH"));
        System.clearProperty("PATH");
    }
}
