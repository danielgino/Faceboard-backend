package org.example.apimywebsite.configuration;


import jakarta.servlet.http.HttpServletResponse;
import org.example.apimywebsite.util.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.LinkedHashMap;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // M-SEC1 fix: authentication is fully JWT-per-request (JwtAuthFilter re-derives
                // the principal from the Authorization header on every request; no code path
                // reads/writes HttpSession, and WebSocket/STOMP auth is independently handled by
                // JwtChannelInterceptor per CONNECT frame, unrelated to this HTTP session
                // policy) - confirmed via repo-wide search before adding this. STATELESS forces
                // Spring Security to never create or consult an HttpSession for the
                // SecurityContext, closing the latent (previously default IF_REQUIRED) session
                // pathway without changing any currently-relied-upon behavior.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/user/register", "/ws/**").permitAll()
                        .requestMatchers("/auth/forgot-password").permitAll()
                        .requestMatchers("/auth/reset-password").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(unauthenticatedEntryPoint()))
                .formLogin(form -> form.disable())
                .build();
    }

    // httpBasic() removed: it added a BasicAuthenticationFilter backed only by Spring Boot's
    // auto-generated default user (no UserDetailsService/AuthenticationProvider ties it to real
    // accounts), a redundant authentication surface alongside JWT Bearer. Removing httpBasic()
    // alone drops the entry point it registered too, which would otherwise make Spring Security
    // fall back to Http403ForbiddenEntryPoint (401 -> 403 regression). This reproduces
    // HttpBasicConfigurer's own default entry point split - X-Requested-With: XMLHttpRequest gets
    // a status-only 401 (HttpStatusEntryPoint, unchanged), everything else gets the same
    // sendError(401, "Unauthorized") BasicAuthenticationEntryPoint already sends - minus the
    // WWW-Authenticate: Basic challenge, since Basic auth is no longer offered.
    private AuthenticationEntryPoint unauthenticatedEntryPoint() {
        AuthenticationEntryPoint defaultEntryPoint = (request, response, authException) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase());

        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"),
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));

        DelegatingAuthenticationEntryPoint delegatingEntryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegatingEntryPoint.setDefaultEntryPoint(defaultEntryPoint);
        return delegatingEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "https://faceboard-frontend.vercel.app"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    }