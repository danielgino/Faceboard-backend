package org.example.apimywebsite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// contextLoads fix: this smoke test boots the *entire* application context (every controller,
// service, security filter chain, WebSocket config, etc.), which needs a real, reachable
// datasource - unlike production, nothing here can rely on ApiMyWebsiteApplication.main()'s
// .env-to-system-property bootstrap, since @SpringBootTest never invokes main(). The "test"
// profile (src/test/resources/application-test.properties) supplies an isolated in-memory H2
// instance and placeholder values for every other required property, so the real application
// context - not a trimmed-down substitute - actually boots, without touching production
// infrastructure, real credentials, or production's own datasource validation settings.
@SpringBootTest
@ActiveProfiles("test")
class ApiMyWebsiteApplicationTests {

    @Test
    void contextLoads() {
    }

}
