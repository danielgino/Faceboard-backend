package org.example.apimywebsite.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// L-OOP3: static utility methods are kept exactly as they were (WebSocketController,
// WebSocketDisconnectListener, and MessageService all call these statically - none of them
// change) - @Component exists solely so Spring can manage one instance to run the @Scheduled
// sweep below against the same static map.
@Component
public class ActiveChatTracker {
    private static final Logger log = LoggerFactory.getLogger(ActiveChatTracker.class);

    // TTL choice: the frontend's sendActiveChatStatus (Chat.js) fires only on chat
    // select/switch/close/unmount - there is no periodic heartbeat re-sending it while a chat
    // stays open. lastUpdated therefore only advances when the user actually switches into (or
    // out of) a conversation, not on a timer. A short TTL would incorrectly expire a
    // conversation a user has genuinely had open (and been actively receiving messages in) for
    // longer than the TTL, breaking auto-read-marking for long-lived sessions - a real
    // regression, not a fix. 12 hours is long enough that it will essentially never affect a
    // real, continuously-open chat session, while still bounding the worst case (a crashed
    // browser/dropped connection that never sent a clean "chattingWith: null") from "forever"
    // (today) down to at most half a day.
    // Not `final` solely so ActiveChatTrackerTest can shrink it temporarily to exercise
    // staleness/eviction without a real 12-hour wait; restored in that test's @AfterEach.
    static long TTL_MILLIS = 12 * 60 * 60 * 1000L;

    private record ActiveChatEntry(int chattingWithId, long lastUpdatedMillis) {}

    private static final ConcurrentHashMap<Integer, ActiveChatEntry> activeChats = new ConcurrentHashMap<>();

    public static void setActiveChat(int userId, int chattingWithId) {
        activeChats.put(userId, new ActiveChatEntry(chattingWithId, System.currentTimeMillis()));
    }

    public static void removeActiveChat(int userId) {
        activeChats.remove(userId);
    }

    // Checks freshness (not just the last-known chattingWith id) so correctness never depends on
    // how recently the scheduled sweep below happened to run - a stale entry stops counting as
    // "actively chatting" the instant it crosses TTL_MILLIS, not just whenever it's next swept.
    public static boolean isUserInChatWith(int userId, int otherUserId) {
        ActiveChatEntry entry = activeChats.get(userId);
        if (entry == null || entry.chattingWithId() != otherUserId) {
            return false;
        }
        return System.currentTimeMillis() - entry.lastUpdatedMillis() <= TTL_MILLIS;
    }

    public static Map<Integer, Integer> getAll() {
        Map<Integer, Integer> snapshot = new HashMap<>();
        activeChats.forEach((userId, entry) -> snapshot.put(userId, entry.chattingWithId()));
        return snapshot;
    }

    // Safety-net memory reclaim only - isUserInChatWith already ignores stale entries on every
    // read regardless of whether this has run yet, so this exists purely to stop the map from
    // growing unboundedly from crashed/dropped sessions that never sent a clean disconnect,
    // not for read-marking correctness. Hourly is frequent enough given a 12h TTL.
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    public void evictStaleEntries() {
        long now = System.currentTimeMillis();
        int before = activeChats.size();
        activeChats.entrySet().removeIf(e -> now - e.getValue().lastUpdatedMillis() > TTL_MILLIS);
        int removed = before - activeChats.size();
        if (removed > 0) {
            log.debug("Evicted {} stale active-chat entries", removed);
        }
    }
}
