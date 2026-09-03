package org.example.apimywebsite.configuration;

import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production-safety regression test, written after an incident where a local
 * `spring-boot:run -Dspring-boot.run.profiles=test` invocation ended up connected to the real
 * production database instead of the intended H2 instance: `spring-boot:run` does not put
 * src/test/resources on its runtime classpath the way `mvn test`/@SpringBootTest does, so
 * application-test.properties was never loaded, and ApiMyWebsiteApplication.main()'s
 * unconditional .env loading (System.setProperty for DB_URL/etc., which outranks
 * application-{profile}.properties in Spring's property precedence) silently supplied the real
 * production JDBC URL instead.
 *
 * Two independent guards were added and are both verified here:
 * 1. ApiMyWebsiteApplication.main() now skips loading .env entirely whenever the "test" profile
 *    is active, so a real DB_URL can never even reach a system property in that case.
 * 2. DemoDataSeeder's CommandLineRunner refuses to seed whenever the "test" profile is active,
 *    regardless of app.demo.enabled - a defense-in-depth backstop that would have prevented the
 *    incident's actual damage (seed rows written to a real database) even if guard #1 had somehow
 *    not applied.
 *
 * This test exercises guard #2 by forcing app.demo.enabled=true via @TestPropertySource (the
 * highest-precedence property source in a Spring test, overriding the file-based default) and
 * confirming demo_user is never created - proving the profile check, not the flag being off, is
 * what stops seeding here.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.demo.enabled=true")
class TestProfileProductionSafetyTest {

    @Autowired
    private Environment environment;
    @Autowired
    private UserRepository userRepository;

    @Test
    void testProfile_datasourceIsH2_neverTheProductionHost() {
        String url = environment.getProperty("spring.datasource.url");
        assertNotNull(url, "spring.datasource.url must resolve to something under the test profile");
        assertTrue(url.toLowerCase().contains("h2:mem"),
                "test profile must use an in-memory H2 datasource, was: " + url);
        assertFalse(url.contains("aivencloud.com"),
                "test profile datasource url must never point at the production host");
    }

    @Test
    void testProfile_demoSeedingIsSkipped_evenWithFeatureFlagForcedOn() {
        // app.demo.enabled=true is forced above; if DemoDataSeeder's profile guard did not
        // exist, this context's own startup would have seeded demo_user already by the time
        // this test method runs. Its absence is exactly what proves the guard fired.
        assertNull(userRepository.findByUserName(DemoDataSeeder.DEMO_USERNAME),
                "demo_user must never be seeded while the 'test' profile is active, regardless of app.demo.enabled");
    }
}
