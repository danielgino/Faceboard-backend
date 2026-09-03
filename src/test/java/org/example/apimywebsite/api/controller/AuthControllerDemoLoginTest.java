package org.example.apimywebsite.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Demo Mode: POST /auth/demo - feature flag, rate limiting, and client-IP resolution.
 * resolveClientIp is private, so it's exercised indirectly here by asserting the exact rate-
 * limiter key AuthController derives from various X-Forwarded-For shapes (ArgumentCaptor),
 * proving the hardening fix (trust the LAST hop, never the first/attacker-suppliable one) - see
 * AuthController.resolveClientIp's Javadoc for why.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerDemoLoginTest {

    @Mock
    private UserService userService;
    @Mock
    private InMemoryRateLimiter rateLimiter;
    @Mock
    private HttpServletRequest request;

    private AuthController controller() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "rateLimiter", rateLimiter);
        ReflectionTestUtils.setField(controller, "demoEnabled", true);
        return controller;
    }

    @Test
    void demoLogin_featureDisabled_returns404_neverConsultsRateLimiterOrUserService() {
        AuthController controller = controller();
        ReflectionTestUtils.setField(controller, "demoEnabled", false);

        ResponseEntity<?> response = controller.demoLogin(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(rateLimiter, userService, request);
    }

    @Test
    void demoLogin_rateLimitExceeded_returns429_neverIssuesToken() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(rateLimiter.tryConsume(eq("demo-issue:203.0.113.5"), anyInt(), anyLong())).thenReturn(false);

        ResponseEntity<?> response = controller().demoLogin(request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verifyNoInteractions(userService);
    }

    @Test
    void demoLogin_seederNotYetRun_returns503() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn(null);

        ResponseEntity<?> response = controller().demoLogin(request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void demoLogin_allowed_returnsTokenFromUserService() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn("a-demo-jwt");

        ResponseEntity<?> response = controller().demoLogin(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("a-demo-jwt", response.getBody());
    }

    @Test
    void resolveClientIp_noForwardedHeader_fallsBackToRemoteAddr() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn("token");

        controller().demoLogin(request);

        verify(rateLimiter).tryConsume(eq("demo-issue:203.0.113.5"), anyInt(), anyLong());
    }

    @Test
    void resolveClientIp_singleHopForwardedFor_usesThatHop() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn("token");

        controller().demoLogin(request);

        verify(rateLimiter).tryConsume(eq("demo-issue:198.51.100.9"), anyInt(), anyLong());
        // A single-hop header never needs the getRemoteAddr() fallback.
    }

    @Test
    void resolveClientIp_attackerPrependedForwardedFor_trustsOnlyTheLastAppendedHop() {
        // Render appends its own detected value rather than replacing a client-supplied
        // X-Forwarded-For (confirmed via Render's own feedback board - see
        // AuthController.resolveClientIp's Javadoc), so an attacker can freely control every
        // entry except the last one. This is the exact scenario the hardening fix must resist:
        // a spoofed first entry must never become the rate-limit key.
        when(request.getHeader("X-Forwarded-For")).thenReturn("6.6.6.6, 9.9.9.9, 203.0.113.77");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn("token");

        controller().demoLogin(request);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).tryConsume(keyCaptor.capture(), anyInt(), anyLong());
        assertEquals("demo-issue:203.0.113.77", keyCaptor.getValue(),
                "must key on the last (proxy-appended) hop, never the attacker-suppliable first one");
    }

    @Test
    void resolveClientIp_forwardedForWithExtraWhitespace_trimsTheHop() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4,  203.0.113.77  ");
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        when(userService.loginAsDemo()).thenReturn("token");

        controller().demoLogin(request);

        verify(rateLimiter).tryConsume(eq("demo-issue:203.0.113.77"), anyInt(), anyLong());
    }
}
