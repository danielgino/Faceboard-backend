package org.example.apimywebsite.util;

import io.jsonwebtoken.JwtException;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for JwtChannelInterceptor's CONNECT authentication and
 * SUBSCRIBE-destination ownership checks (C6 fix).
 * Scenario convention: A and B = legitimate owners of their own private topics,
 * C = unauthorized third party attempting to read A's/B's private channels.
 */
@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;

    @InjectMocks
    private JwtChannelInterceptor interceptor;

    private static final int USER_A = 1;
    private static final int USER_B = 2;
    private static final int USER_C = 3;

    private Principal principalFor(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("USER")));
    }

    private Message<byte[]> connectMessage(String bearerToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // Mirrors how Spring's real STOMP decoder builds inbound messages: headers stay
        // mutable so interceptors (like setUser() below) can modify them in preSend().
        accessor.setLeaveMutable(true);
        if (bearerToken != null) {
            accessor.setNativeHeader("Authorization", "Bearer " + bearerToken);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connectMessageWithRawAuthorizationHeader(String rawHeaderValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", rawHeaderValue);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ---- CONNECT: authentication ----

    @Test
    void connect_withValidJwt_establishesAuthenticatedPrincipal() {
        when(jwtUtil.extractUsername("valid-token")).thenReturn("userA");
        when(jwtUtil.isTokenValid("valid-token", "userA")).thenReturn(true);
        User user = User.builder().id(USER_A).userName("userA").password("current-hash").build();
        when(userService.findByUserName("userA")).thenReturn(user);
        when(jwtUtil.matchesCurrentPassword("valid-token", "current-hash")).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage("valid-token"), null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resultAccessor.getUser());
        assertEquals("userA", resultAccessor.getUser().getName());
    }

    // SEC-004 fix: a CONNECT with no Authorization header at all used to be let through with no
    // principal (the "anonymous connection policy"). Since isSubscriptionAuthorized already
    // rejects every SUBSCRIBE without a principal - public or private - an anonymous session
    // could never do anything legitimate; it just held a socket open and could still reach
    // mapped SEND handlers. Missing credentials are now rejected exactly like an invalid one.
    @Test
    void connect_withoutToken_isRejectedCleanly() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage(null), null));

        verifyNoInteractions(jwtUtil, userService);
    }

    // ---- CONNECT: a *supplied* invalid credential is rejected cleanly, never silently
    // downgraded to anonymous (H3 follow-up). A *missing* credential (tested above,
    // connect_withoutToken_isRejectedCleanly) is now rejected the same way, not treated as a
    // separate anonymous-connection policy.

    @Test
    void connect_withExpiredToken_isRejectedCleanly() {
        when(jwtUtil.extractUsername("expired-token")).thenThrow(new JwtException("expired"));

        MessagingException ex = assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("expired-token"), null));

        // No internal JWT-parsing details are leaked into the message the client receives.
        assertFalse(ex.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void connect_withMalformedToken_isRejectedCleanly() {
        when(jwtUtil.extractUsername("garbage-token")).thenThrow(new JwtException("malformed"));

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("garbage-token"), null));
    }

    @Test
    void connect_withWrongSignatureToken_isRejectedCleanly() {
        when(jwtUtil.extractUsername("wrong-signature-token")).thenThrow(new JwtException("bad signature"));

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("wrong-signature-token"), null));
    }

    @Test
    void connect_withEmptyBearerToken_isRejectedCleanly() {
        when(jwtUtil.extractUsername("")).thenThrow(new IllegalArgumentException("JWT String argument cannot be null or empty"));

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage(""), null));
    }

    @Test
    void connect_withTokenFailingValidityCheck_isRejectedCleanly() {
        // isTokenValid returning false without throwing (e.g. a defensive expiration
        // re-check) must reject exactly like a thrown JwtException - not silently proceed
        // with no principal.
        when(jwtUtil.extractUsername("stale-token")).thenReturn("userA");
        when(jwtUtil.isTokenValid("stale-token", "userA")).thenReturn(false);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("stale-token"), null));

        verifyNoInteractions(userService);
    }

    @Test
    void connect_withValidTokenForDeletedUser_isRejectedCleanly() {
        // A well-formed, unexpired, correctly-signed token whose subject no longer resolves
        // to an existing account (e.g. deleted after issuance) must not silently connect
        // anonymously either.
        when(jwtUtil.extractUsername("valid-token")).thenReturn("ghostUser");
        when(jwtUtil.isTokenValid("valid-token", "ghostUser")).thenReturn(true);
        when(userService.findByUserName("ghostUser")).thenReturn(null);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("valid-token"), null));
    }

    // SEC-005 fix: a validly signed, unexpired token issued before the user's password was
    // changed/reset must be rejected at CONNECT too, mirroring JwtAuthFilter's REST check.
    @Test
    void connect_withTokenIssuedBeforePasswordChange_isRejectedCleanly() {
        when(jwtUtil.extractUsername("stale-token")).thenReturn("userA");
        when(jwtUtil.isTokenValid("stale-token", "userA")).thenReturn(true);
        User user = User.builder().id(USER_A).userName("userA").password("new-hash-after-password-change").build();
        when(userService.findByUserName("userA")).thenReturn(user);
        when(jwtUtil.matchesCurrentPassword("stale-token", "new-hash-after-password-change")).thenReturn(false);

        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessage("stale-token"), null));
    }

    @Test
    void connect_withMalformedAuthorizationHeader_isRejectedCleanly() {
        // Present but not a recognizable "Bearer <token>" credential.
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(connectMessageWithRawAuthorizationHeader("garbage-header-value"), null));

        verifyNoInteractions(jwtUtil);
    }

    @Test
    void reconnect_withFreshValidTokenAfterARejectedAttempt_stillEstablishesAuthenticatedPrincipal() {
        // Simulates a client whose first CONNECT used a stale/expired token (rejected),
        // then reconnects with a fresh valid one — the fresh attempt must succeed normally.
        when(jwtUtil.extractUsername("expired-token")).thenThrow(new JwtException("expired"));
        assertThrows(MessagingException.class, () -> interceptor.preSend(connectMessage("expired-token"), null));

        when(jwtUtil.extractUsername("fresh-token")).thenReturn("userA");
        when(jwtUtil.isTokenValid("fresh-token", "userA")).thenReturn(true);
        User user = User.builder().id(USER_A).userName("userA").password("current-hash").build();
        when(userService.findByUserName("userA")).thenReturn(user);
        when(jwtUtil.matchesCurrentPassword("fresh-token", "current-hash")).thenReturn(true);

        Message<?> reconnect = interceptor.preSend(connectMessage("fresh-token"), null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(reconnect);
        assertNotNull(resultAccessor.getUser());
        assertEquals("userA", resultAccessor.getUser().getName());
    }

    // ---- SUBSCRIBE: legitimate access to own private destinations ----

    @Test
    void subscribe_ownMessagesDestination_isAllowed_forA() {
        when(userService.findByUserName("userA")).thenReturn(User.builder().id(USER_A).userName("userA").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/messages/" + USER_A, principalFor("userA")), null);

        assertNotNull(result);
    }

    @Test
    void subscribe_ownNotificationsDestination_isAllowed_forB() {
        when(userService.findByUserName("userB")).thenReturn(User.builder().id(USER_B).userName("userB").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/notifications/" + USER_B, principalFor("userB")), null);

        assertNotNull(result);
    }

    @Test
    void subscribe_ownMessageReadDestination_isAllowed_forA() {
        when(userService.findByUserName("userA")).thenReturn(User.builder().id(USER_A).userName("userA").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/message-read/" + USER_A, principalFor("userA")), null);

        assertNotNull(result);
    }

    @Test
    void subscribe_ownDestination_isAllowed_forUserIdAboveIntegerCacheRange() {
        // Regression test: currentUser.getId() (int) vs the regex-captured ownerId (Integer)
        // must be compared by value, not by == reference identity, which would incorrectly
        // reject legitimate users whose id falls outside the JVM's cached Integer range
        // (-128..127, e.g. autoboxing quirks for id 1000).
        int userId = 1000;
        when(userService.findByUserName("userLarge")).thenReturn(
                User.builder().id(userId).userName("userLarge").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/messages/" + userId, principalFor("userLarge")), null);

        assertNotNull(result);
    }

    // ---- SUBSCRIBE: cross-user rejection ----

    @Test
    void subscribe_bUsersMessagesDestination_isRejected_whenAttemptedByA() {
        when(userService.findByUserName("userA")).thenReturn(User.builder().id(USER_A).userName("userA").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/messages/" + USER_B, principalFor("userA")), null);

        assertNull(result);
    }

    @Test
    void subscribe_asUsersNotificationsDestination_isRejected_whenAttemptedByC() {
        when(userService.findByUserName("userC")).thenReturn(User.builder().id(USER_C).userName("userC").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/notifications/" + USER_A, principalFor("userC")), null);

        assertNull(result);
    }

    @Test
    void subscribe_bUsersMessageReadDestination_isRejected_whenAttemptedByC() {
        when(userService.findByUserName("userC")).thenReturn(User.builder().id(USER_C).userName("userC").build());

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/message-read/" + USER_B, principalFor("userC")), null);

        assertNull(result);
    }

    // ---- SUBSCRIBE: unauthenticated ----

    @Test
    void subscribe_withoutAuthenticatedPrincipal_isRejected() {
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/messages/" + USER_A, null), null);

        assertNull(result);
        verifyNoInteractions(userService);
    }

    // ---- SUBSCRIBE: malformed / unknown destinations rejected by default ----

    @Test
    void subscribe_nonNumericUserSegment_isRejected() {
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/messages/not-a-number", principalFor("userA")), null);

        assertNull(result);
    }

    @Test
    void subscribe_unrecognizedTopic_isRejectedByDefault() {
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/some-other-channel/" + USER_A, principalFor("userA")), null);

        assertNull(result);
    }

    @Test
    void subscribe_missingDestination_isRejected() {
        Message<?> result = interceptor.preSend(
                subscribeMessage(null, principalFor("userA")), null);

        assertNull(result);
    }

    // ---- SUBSCRIBE: public destination ----

    @Test
    void subscribe_publicPostsDestination_isAllowedForAnyAuthenticatedUser() {
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/posts", principalFor("userA")), null);

        assertNotNull(result);
        verifyNoInteractions(userService);
    }

    @Test
    void subscribe_publicPostsDestination_withoutAuthenticatedPrincipal_isRejected() {
        // Policy: no subscription — public or private — is served over an
        // unauthenticated connection; only the per-owner check is skipped for public topics.
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/posts", null), null);

        assertNull(result);
        verifyNoInteractions(userService);
    }
}
