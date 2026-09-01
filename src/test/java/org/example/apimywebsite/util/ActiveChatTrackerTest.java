package org.example.apimywebsite.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L-OOP3: ActiveChatTracker now stores a lastUpdated timestamp per entry and expires entries
 * older than a TTL, both on read (isUserInChatWith) and via a periodic sweep (evictStaleEntries)
 * that reclaims memory from entries a clean disconnect never removed. TTL_MILLIS is shrunk here
 * to a few milliseconds so staleness can be exercised with real (short) sleeps instead of the
 * real 12-hour TTL, and restored afterward so it never leaks into other test classes sharing the
 * same static state.
 */
class ActiveChatTrackerTest {

    private static final int USER_A = 101;
    private static final int USER_B = 202;
    private static final int PEER = 999;

    private long originalTtl;

    @BeforeEach
    void shrinkTtl() {
        originalTtl = ActiveChatTracker.TTL_MILLIS;
        ActiveChatTracker.TTL_MILLIS = 100;
    }

    @AfterEach
    void restoreTtlAndCleanUp() {
        ActiveChatTracker.TTL_MILLIS = originalTtl;
        ActiveChatTracker.removeActiveChat(USER_A);
        ActiveChatTracker.removeActiveChat(USER_B);
    }

    @Test
    void setActiveChat_thenIsUserInChatWith_returnsTrueForTheMatchingPair() {
        ActiveChatTracker.setActiveChat(USER_A, PEER);

        assertTrue(ActiveChatTracker.isUserInChatWith(USER_A, PEER));
    }

    @Test
    void isUserInChatWith_returnsFalse_forADifferentOtherUser() {
        ActiveChatTracker.setActiveChat(USER_A, PEER);

        assertFalse(ActiveChatTracker.isUserInChatWith(USER_A, PEER + 1));
    }

    @Test
    void removeActiveChat_clearsTheEntry_preservingExistingBehavior() {
        ActiveChatTracker.setActiveChat(USER_A, PEER);

        ActiveChatTracker.removeActiveChat(USER_A);

        assertFalse(ActiveChatTracker.isUserInChatWith(USER_A, PEER));
    }

    @Test
    void isUserInChatWith_returnsFalse_onceTheEntryExceedsTheTtl() throws InterruptedException {
        ActiveChatTracker.setActiveChat(USER_A, PEER);

        Thread.sleep(150); // > the 100ms TTL shrunk in @BeforeEach

        assertFalse(ActiveChatTracker.isUserInChatWith(USER_A, PEER));
    }

    @Test
    void setActiveChat_refreshesLastUpdated_soARenewedEntryStaysFreshPastTheOldDeadline() throws InterruptedException {
        ActiveChatTracker.setActiveChat(USER_A, PEER);
        Thread.sleep(60);
        ActiveChatTracker.setActiveChat(USER_A, PEER); // re-affirmed before the original 100ms TTL would have elapsed
        Thread.sleep(60); // now 120ms since the first call, but only 60ms since the refresh

        assertTrue(ActiveChatTracker.isUserInChatWith(USER_A, PEER));
    }

    @Test
    void evictStaleEntries_removesOnlyExpiredEntries_keepsFreshOnesUntouched() throws InterruptedException {
        ActiveChatTracker.setActiveChat(USER_A, PEER); // will be stale
        Thread.sleep(150);
        ActiveChatTracker.setActiveChat(USER_B, PEER); // fresh

        new ActiveChatTracker().evictStaleEntries();

        assertFalse(ActiveChatTracker.getAll().containsKey(USER_A));
        assertTrue(ActiveChatTracker.getAll().containsKey(USER_B));
    }
}
