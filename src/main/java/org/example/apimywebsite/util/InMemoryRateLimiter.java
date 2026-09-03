package org.example.apimywebsite.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Demo Mode rate limiting. Deliberately a small hand-rolled fixed-window limiter rather than a
// library (e.g. Bucket4j) - the algorithm needed here (N events per rolling window, keyed by an
// arbitrary string) is a few dozen lines, and this single-instance Render deployment (confirmed
// via render.yaml: one `services:` entry, no autoscaling/instance-count config) has no
// distributed-cache requirement that would justify a new dependency for it. Bounded via
// opportunistic sweeping of stale entries (see maybeEvictStale) instead of a scheduled task, so
// no @EnableScheduling wiring is needed anywhere in the app just for this to stay bounded.
// Revisit with a shared store (e.g. Redis) only if the deployment ever becomes multi-instance -
// per-instance buckets would otherwise let an attacker multiply their effective limit.
@Component
public class InMemoryRateLimiter {

    private static final int SWEEP_EVERY_N_CALLS = 500;
    private static final long STALE_AFTER_MILLIS = 3_600_000; // 1 hour

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong();

    public boolean tryConsume(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now));

        boolean allowed;
        synchronized (window) {
            if (now - window.windowStart >= windowMillis) {
                window.windowStart = now;
                window.count.set(0);
            }
            allowed = window.count.incrementAndGet() <= limit;
        }

        if (callCounter.incrementAndGet() % SWEEP_EVERY_N_CALLS == 0) {
            evictStale(now);
        }
        return allowed;
    }

    private void evictStale(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart > STALE_AFTER_MILLIS);
    }

    private static final class Window {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
