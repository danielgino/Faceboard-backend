package org.example.apimywebsite.util;

import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.JwtException;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    // Private, per-user topics: the numeric segment must match the authenticated user's id.
    private static final List<Pattern> PRIVATE_USER_DESTINATIONS = List.of(
            Pattern.compile("^/topic/messages/(\\d+)$"),
            Pattern.compile("^/topic/notifications/(\\d+)$"),
            Pattern.compile("^/topic/message-read/(\\d+)$")
    );

    // Destinations with no per-user ownership (broadcast to every subscriber by design).
    private static final List<String> PUBLIC_DESTINATIONS = List.of("/topic/posts");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // SEC-004 fix: a CONNECT with no Authorization header at all used to be let through
            // anonymously (isSubscriptionAuthorized already rejects every SUBSCRIBE - public or
            // private - without a principal, so an anonymous session could never do anything
            // legitimate; it just sat there consuming a socket/channel and could still reach
            // mapped SEND handlers to throw/log). Missing credentials are now rejected exactly
            // like an invalid/malformed one, closing the session at CONNECT instead of leaving it
            // open to no useful end. The frontend already always sends a Bearer token on connect
            // (WebSocketProvider never calls connect() without a logged-in user), so this is not
            // reachable from any current legitimate client flow.
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new MessagingException("Invalid authentication credentials");
            }

            String token = authHeader.substring(7);
            try {
                String username = jwtUtil.extractUsername(token);

                if (username != null && jwtUtil.isTokenValid(token, username)) {
                    User user = userService.findByUserName(username);
                    // Demo Mode: no WebSocket session at all for ROLE_DEMO - rejected the same
                    // way as a missing/invalid Authorization header (SEC-004 fix below), not
                    // merely restricted at SUBSCRIBE/SEND. Simpler and smaller surface than
                    // allowing CONNECT and gating individual frames: there's never a session to
                    // subscribe or send from.
                    if (user != null && user.isDemo()) {
                        throw new MessagingException("Invalid authentication credentials");
                    }
                    // SEC-005 fix: matchesCurrentPassword rejects a token issued before the
                    // user's password was last changed/reset, mirroring JwtAuthFilter's REST check.
                    if (user != null && jwtUtil.matchesCurrentPassword(token, user.getPassword())) {
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                user.getUserName(), null, List.of(new SimpleGrantedAuthority("USER"))
                        );
                        accessor.setUser(auth);
                    } else {
                        throw new MessagingException("Invalid authentication credentials");
                    }
                } else {
                    throw new MessagingException("Invalid authentication credentials");
                }
            } catch (JwtException | IllegalArgumentException e) {
                // The supplied token is invalid/expired/malformed/mis-signed: reject the
                // CONNECT cleanly. Spring turns this into a STOMP ERROR frame (using only
                // this generic message, never the raw parsing exception) and closes the
                // session — no internal exception details are exposed to the client.
                throw new MessagingException("Invalid authentication credentials");
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!isSubscriptionAuthorized(accessor)) {
                // Silently drop this single subscription request; the session and any
                // other, already-authorized subscriptions on it are left untouched.
                return null;
            }
        }

        return message;
    }

    private boolean isSubscriptionAuthorized(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return false;
        }

        // No subscription — public or private — is served over an unauthenticated
        // connection; only the per-owner check below is skipped for public destinations.
        Principal principal = accessor.getUser();
        if (principal == null) {
            return false;
        }

        if (PUBLIC_DESTINATIONS.contains(destination)) {
            return true;
        }

        Integer ownerId = resolvePrivateDestinationOwnerId(destination);
        if (ownerId == null) {
            // Unknown or malformed private destination: reject by default.
            return false;
        }

        User currentUser = userService.findByUserName(principal.getName());
        // currentUser.getId() is a primitive int and ownerId is a boxed Integer; use
        // Objects.equals (rather than ==) so the comparison is unambiguously value-based
        // regardless of autoboxing/cache-range subtleties.
        return currentUser != null && Objects.equals(currentUser.getId(), ownerId);
    }

    private Integer resolvePrivateDestinationOwnerId(String destination) {
        for (Pattern pattern : PRIVATE_USER_DESTINATIONS) {
            Matcher matcher = pattern.matcher(destination);
            if (matcher.matches()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }
}