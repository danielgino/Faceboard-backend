package org.example.apimywebsite.util;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.cloudinary.AccessControlRule.AccessType.token;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String username = jwtUtil.extractUsername(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userRepository.findByUserName(username);
                    // SEC-005 fix: matchesCurrentPassword rejects a token issued before the
                    // user's password was last changed/reset, even though it's otherwise a
                    // validly signed, unexpired token for this username.
                    if (user != null && jwtUtil.isTokenValid(token, username)
                            && jwtUtil.matchesCurrentPassword(token, user.getPassword())) {
                        // Demo Mode: the seeded demo_user (and only that account) gets ROLE_DEMO
                        // instead of ROLE_USER - every other authenticated request is completely
                        // unaffected, since user.isDemo() defaults to false for every real row.
                        String authority = user.isDemo() ? "ROLE_DEMO" : "ROLE_USER";
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                username, null,
                                List.of(new SimpleGrantedAuthority(authority))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        if (user.isDemo()) {
                            // DemoAccessFilter (registered right after this filter) rate-limits
                            // per issued token rather than per shared identity - stash the jti
                            // here so it doesn't need to re-parse the token itself.
                            request.setAttribute("demoJti", jwtUtil.extractJti(token));
                        }
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Invalid/expired/malformed/mis-signed token: leave the request unauthenticated
                // so Spring Security's own filter chain produces the normal 401/403 response,
                // instead of letting a JWT parsing exception surface as an unhandled 500.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}