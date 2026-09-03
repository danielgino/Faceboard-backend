package org.example.apimywebsite.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demo Mode: direct coverage of the real (non-mocked) limiter backing /auth/demo's 429 behavior
 * - AuthControllerDemoLoginTest covers the controller's handling of a limiter result, but mocks
 * the limiter itself; this proves the actual counting/window logic.
 */
class InMemoryRateLimiterTest {

    @Test
    void tryConsume_allowsUpToLimit_thenDenies() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        String key = "demo-issue:203.0.113.5";

        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryConsume(key, 20, 3_600_000), "request " + (i + 1) + " of 20 must be allowed");
        }
        assertFalse(limiter.tryConsume(key, 20, 3_600_000), "the 21st request within the window must be denied (429)");
    }

    @Test
    void tryConsume_differentKeys_areIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();

        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryConsume("demo-issue:203.0.113.5", 20, 3_600_000));
        }
        assertFalse(limiter.tryConsume("demo-issue:203.0.113.5", 20, 3_600_000),
                "first IP must now be rate-limited");
        assertTrue(limiter.tryConsume("demo-issue:198.51.100.9", 20, 3_600_000),
                "a different IP must have its own independent bucket, not share the exhausted one");
    }
}
