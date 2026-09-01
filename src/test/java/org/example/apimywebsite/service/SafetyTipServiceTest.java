package org.example.apimywebsite.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TIP-001: SafetyTipService's curated pool + shuffle/rotation, no Gemini/external calls
 * involved. Focused on the behavior GET /safety-tips/random depends on: always a real tip,
 * no repeat within one full pass over the pool, and safe under concurrent access.
 */
class SafetyTipServiceTest {

    @Test
    void getRandomTip_alwaysReturnsANonBlankTip() {
        SafetyTipService service = new SafetyTipService();

        String tip = service.getRandomTip();

        assertNotNull(tip);
        assertFalse(tip.isBlank());
    }

    @Test
    void getRandomTip_onePassOverThePool_neverRepeatsAnyTip() {
        SafetyTipService service = new SafetyTipService();
        int poolSize = countDistinctTipsOverManyCalls(service);

        Set<String> seenInOnePass = new HashSet<>();
        for (int i = 0; i < poolSize; i++) {
            String tip = service.getRandomTip();
            assertTrue(seenInOnePass.add(tip), "tip repeated within a single pass over the pool: " + tip);
        }
    }

    @Test
    void getRandomTip_afterExhaustingThePool_reshufflesAndKeepsServingRealTips() {
        SafetyTipService service = new SafetyTipService();
        int poolSize = countDistinctTipsOverManyCalls(service);

        // Exhaust one full pass, then pull one more from the reshuffled deck.
        for (int i = 0; i < poolSize; i++) {
            service.getRandomTip();
        }
        String afterReshuffle = service.getRandomTip();

        assertNotNull(afterReshuffle);
        assertFalse(afterReshuffle.isBlank());
    }

    @Test
    void getRandomTip_reshuffleDoesNotImmediatelyRepeatTheLastTipServedBeforeIt() {
        SafetyTipService service = new SafetyTipService();
        int poolSize = countDistinctTipsOverManyCalls(service);

        String lastOfFirstPass = null;
        for (int i = 0; i < poolSize; i++) {
            lastOfFirstPass = service.getRandomTip();
        }
        String firstOfSecondPass = service.getRandomTip();

        assertFalse(firstOfSecondPass.equals(lastOfFirstPass),
                "the tip right after a reshuffle must not immediately repeat the previous tip");
    }

    @Test
    void getRandomTip_isSafeUnderConcurrentAccess() throws Exception {
        SafetyTipService service = new SafetyTipService();
        int threadCount = 20;
        int callsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = new java.util.ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                tasks.add(() -> {
                    for (int i = 0; i < callsPerThread; i++) {
                        String tip = service.getRandomTip();
                        if (tip == null || tip.isBlank()) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            List<Future<Boolean>> results = pool.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertTrue(result.get(), "a concurrent call returned a null/blank tip");
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // Discovers the pool size empirically (rather than hardcoding it here, which would just
    // duplicate - and risk drifting from - SafetyTipService's own TIPS list) by pulling tips
    // until the very first one seen is served again, i.e. exactly one full pass.
    private static int countDistinctTipsOverManyCalls(SafetyTipService service) {
        SafetyTipService probe = new SafetyTipService();
        Set<String> seen = new HashSet<>();
        String tip;
        do {
            tip = probe.getRandomTip();
        } while (seen.add(tip));
        return seen.size();
    }
}
