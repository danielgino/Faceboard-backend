package org.example.apimywebsite.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Demo Mode central gate: everything here is a no-op unless the resolved principal carries
// ROLE_DEMO (granted only to the seeded demo_user by JwtAuthFilter), so real users and
// unauthenticated requests are completely unaffected - registered addFilterAfter(JwtAuthFilter)
// in SecurityConfig. For a ROLE_DEMO caller: any non-GET request is rejected as read-only
// (DEMO_READ_ONLY); any GET outside the explicit allowlist is rejected as out of scope
// (DEMO_ACCESS_DENIED); everything else is per-token rate limited. This filter enforces
// REACHABILITY only - which rows an allowlisted endpoint actually returns is separately scoped
// in the relevant service methods via DemoScope, since that can't be decided from the path alone.
@Component
public class DemoAccessFilter extends OncePerRequestFilter {

    private static final String DEMO_AUTHORITY = "ROLE_DEMO";

    private static final int DEMO_CALL_LIMIT = 60;
    private static final long DEMO_CALL_WINDOW_MILLIS = 60_000;

    // Final Demo allowlist (GET only) - everything not listed here is denied by default.
    // /messages/conversation/{userId}/{otherUserId}: MessageService.getMessagesForConversation
    // additionally requires BOTH participants to be isDemo=true for a ROLE_DEMO caller (see
    // DemoScope calls there) - reachability here is necessary but not sufficient on its own.
    // /notifications, /notifications/unread-count: both already resolve the caller exclusively
    // via AuthHelper.getCurrentUser() (no client-supplied id), so they can only ever return the
    // seeded demo_user's own notifications - no additional service-layer scoping needed.
    // /friendship/status/{userId}/{friendId}: FriendshipController.checkStatus additionally
    // requires BOTH userId and friendId to resolve to isDemo=true users for a ROLE_DEMO caller
    // (DemoScope, mirroring the conversation-endpoint pattern above) - reachability here alone
    // is not sufficient, since the controller's own caller-is-a-party check does not by itself
    // prove the OTHER party is Demo-owned data.
    private static final List<String> ALLOWED_GET_PATTERNS = List.of(
            "/post/feed",
            "/post/posts",
            "/post/{postId}",
            "/post/{postId}/like-count",
            "/comments/post/{postId}",
            "/likes/post/{postId}",
            "/user/by-id",
            "/user/{userId}/friends/page",
            "/user/name",
            "/auth/me",
            "/safety-tips/random",
            "/messages/conversation/{userId}/{otherUserId}",
            "/notifications",
            "/notifications/unread-count",
            "/friendship/status/{userId}/{friendId}"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isDemo = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> DEMO_AUTHORITY.equals(a.getAuthority()));

        if (!isDemo) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            writeDenial(response, "DEMO_READ_ONLY", "Demo mode is read-only.");
            return;
        }

        String path = request.getRequestURI();
        boolean allowed = ALLOWED_GET_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (!allowed) {
            writeDenial(response, "DEMO_ACCESS_DENIED", "This is not available in Demo mode.");
            return;
        }

        // Keyed by the token's jti (set as a request attribute by JwtAuthFilter) rather than the
        // shared demo_user subject or IP, so many simultaneous demo visitors each get an
        // independent bucket - one abusive token gets throttled without affecting anyone else's.
        String jti = (String) request.getAttribute("demoJti");
        String rateKey = "demo-call:" + (jti != null ? jti : request.getRemoteAddr());
        if (!rateLimiter.tryConsume(rateKey, DEMO_CALL_LIMIT, DEMO_CALL_WINDOW_MILLIS)) {
            // 429 has no HttpServletResponse.SC_* constant (jakarta.servlet predates RFC 6585).
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"DEMO_RATE_LIMITED\",\"message\":\"Too many requests.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeDenial(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
