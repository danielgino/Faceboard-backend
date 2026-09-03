package org.example.apimywebsite;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.example.apimywebsite")
@EnableScheduling

public class ApiMyWebsiteApplication {

    public static void main(String[] args) {

        // Production-safety guard: .env carries real production credentials, and
        // System.setProperty below makes them outrank application-{profile}.properties in
        // Spring's own property precedence (System properties sit above profile-specific
        // files). That ordering is exactly backwards for the "test" profile - if a test run
        // ever doesn't end up loading application-test.properties for any reason (e.g. its
        // resources aren't on the runtime classpath, as happens with a bare `spring-boot:run
        // -Dspring-boot.run.profiles=test`, which - unlike `mvn test`/@SpringBootTest - does
        // NOT include src/test/resources by default), these system properties silently point
        // the app at production instead of failing loudly. Skipping the entire load whenever
        // "test" is the active profile removes that failure mode at the source:
        // application-test.properties' own H2/placeholder values are then the only source
        // for these keys, with nothing able to override them from a real .env.
        if (!isTestProfileActive()) {
            // Loads a local .env file when present (development); does not fail when it's
            // absent (e.g. Render, where these are supplied as real platform environment
            // variables instead — Dotenv.get() already checks System.getenv() first).
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            setPropertyIfPresent(dotenv, "DB_URL");
            setPropertyIfPresent(dotenv, "DB_USERNAME");
            setPropertyIfPresent(dotenv, "DB_PASSWORD");
            setPropertyIfPresent(dotenv, "CLOUDINARY_NAME");
            setPropertyIfPresent(dotenv, "CLOUDINARY_API_KEY");
            setPropertyIfPresent(dotenv, "CLOUDINARY_API_SECRET");
            setPropertyIfPresent(dotenv, "JWT_SECRET");
            setPropertyIfPresent(dotenv, "SMTP_EMAIL");
            setPropertyIfPresent(dotenv, "SMTP_PASSWORD");
        }

        SpringApplication.run(ApiMyWebsiteApplication.class, args);
        System.out.println("Application started!✅");

    }

    // Checks both common spellings of the active-profile flag (-Dspring.profiles.active=test
    // and the SPRING_PROFILES_ACTIVE env var Render/most platforms use for the same setting)
    // so this guard fires regardless of which one a given invocation uses. Deliberately reads
    // the raw flag directly, before Spring's own Environment exists yet - this runs ahead of
    // SpringApplication.run() specifically to decide whether .env should load at all.
    private static boolean isTestProfileActive() {
        String active = System.getProperty("spring.profiles.active", System.getenv("SPRING_PROFILES_ACTIVE"));
        if (active == null || active.isBlank()) {
            return false;
        }
        for (String profile : active.split(",")) {
            if ("test".equals(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    // System.setProperty(key, value) throws NullPointerException on a null value, which
    // would otherwise crash startup here (before Spring even runs) for any key missing
    // from both the .env file and the real environment. Skipping absent keys lets Spring's
    // own Environment resolution fall through to a genuine OS environment variable if one
    // exists, or — for JWT_SECRET specifically — lets JwtUtil's own fail-fast check produce
    // a clear, intentional startup failure instead of this unrelated NPE.
    private static void setPropertyIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null) {
            System.setProperty(key, value);
        }
    }

}
