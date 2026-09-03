package org.example.apimywebsite.util;

import org.example.apimywebsite.service.DemoDataSeederService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

// Demo Mode: thin CommandLineRunner registrar, gated on app.demo.enabled. The actual seeding
// (idempotent, transactional, with partial-state recovery) lives in DemoDataSeederService - a
// real @Service bean, so its @Transactional method is called through Spring's proxy from here
// rather than via same-class self-invocation (which would silently skip the transaction).
@Configuration
public class DemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_USERNAME = "demo_user";

    @Bean
    public CommandLineRunner demoDataSeederRunner(
            @Value("${app.demo.enabled:false}") boolean demoEnabled,
            Environment environment,
            DemoDataSeederService demoDataSeederService) {
        return args -> {
            if (!demoEnabled) {
                return;
            }
            // Production-safety guard (defense-in-depth, independent of ApiMyWebsiteApplication's
            // own .env-loading guard): the "test" profile is exclusively for automated test JVMs
            // (@SpringBootTest against an ephemeral H2 instance) - seeding demo rows there is
            // never intentional, so this refuses outright whenever "test" is active, regardless
            // of what datasource the app actually resolved to. This is the check that would have
            // caught the incident that motivated it even if the app had, for whatever reason,
            // still ended up pointed at a real database while "test" was active.
            if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
                log.warn("Demo Mode seeding skipped: the 'test' Spring profile is active. " +
                        "Demo seeding never runs under the test profile.");
                return;
            }
            try {
                demoDataSeederService.seedIfNeeded();
            } catch (Exception e) {
                log.error("Demo data seeding failed", e);
            }
        };
    }
}
