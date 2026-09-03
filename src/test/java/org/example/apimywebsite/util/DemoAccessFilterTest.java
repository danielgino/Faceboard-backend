package org.example.apimywebsite.util;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Demo Mode: verifies the two new allowlist entries added for Chat/Notifications (both required
 * for the "Demo Chat/Notifications" feature) without regressing the deny-by-default behavior -
 * in particular that /messages/unread-summary/{userId} (deliberately excluded - it can reveal
 * that a real user has messaged demo_user) stays denied even though it lives right next to the
 * now-allowed /messages/conversation/{userId}/{otherUserId}.
 */
@ExtendWith(MockitoExtension.class)
class DemoAccessFilterTest {

    @Mock
    private InMemoryRateLimiter rateLimiter;
    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsDemo() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("demo_user", null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));
    }

    private DemoAccessFilter newFilter() {
        DemoAccessFilter f = new DemoAccessFilter();
        ReflectionTestUtils.setField(f, "rateLimiter", rateLimiter);
        return f;
    }

    @Test
    void nonDemoRequest_isNoOp_regardlessOfPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/post/add");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void demoRequest_conversationEndpoint_isAllowed() throws Exception {
        loginAsDemo();
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/messages/conversation/38/39");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void demoRequest_unreadSummaryEndpoint_remainsDenied() throws Exception {
        loginAsDemo();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/messages/unread-summary/38");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_ACCESS_DENIED"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void demoRequest_sendMessageEndpoint_isBlocked_notAGetRequest() throws Exception {
        loginAsDemo();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/messages/send");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_READ_ONLY"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void demoRequest_notificationsEndpoint_isAllowed() throws Exception {
        loginAsDemo();
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void demoRequest_notificationsUnreadCountEndpoint_isAllowed() throws Exception {
        loginAsDemo();
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications/unread-count");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void demoRequest_markAllAsReadEndpoint_isBlocked_notAGetRequest() throws Exception {
        loginAsDemo();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notifications/mark-all-as-read");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_READ_ONLY"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void demoRequest_friendshipStatusEndpoint_isAllowed() throws Exception {
        loginAsDemo();
        when(rateLimiter.tryConsume(any(), anyInt(), anyLong())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friendship/status/38/39");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void demoRequest_friendshipSendEndpoint_isBlocked_notAGetRequest() throws Exception {
        loginAsDemo();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/friendship/send/38/39");
        MockHttpServletResponse response = new MockHttpServletResponse();

        newFilter().doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEMO_READ_ONLY"));
        verifyNoInteractions(filterChain);
    }
}
