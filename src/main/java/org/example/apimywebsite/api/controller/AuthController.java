package org.example.apimywebsite.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.LoginRequestDTO;
import org.example.apimywebsite.dto.PasswordResetDTO;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.service.PasswordResetService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthHelper authHelper;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private InMemoryRateLimiter rateLimiter;

    // Demo Mode kill switch: DemoDataSeeder also reads this same property, so turning it off
    // both stops new seeding and closes this endpoint - existing tokens simply expire within
    // their 15-minute TTL, no revocation needed.
    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    private static final int DEMO_ISSUE_LIMIT = 20;
    private static final long DEMO_ISSUE_WINDOW_MILLIS = 3_600_000; // 1 hour

@PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequest) {
    try {
        String token = userService.loginByEmail(loginRequest.getEmail(), loginRequest.getPassword());
        return ResponseEntity.ok(token);
    } catch (ResponseStatusException ex) {
        if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }
        throw ex;
    }
}
    // M-OOP1: /auth/me is permitAll() (unlike every other AuthHelper consumer, which sits behind
    // .anyRequest().authenticated()), so it's the one REST path where a request can reach the
    // controller without Spring Security's authorization filter having already rejected an
    // unauthenticated caller - this null-check is therefore still required (unlike
    // CommentService/UserController's migrations, which needed no new guard). JwtAuthFilter
    // already reads the Authorization header, validates the JWT, and populates the
    // SecurityContext before this method runs; AuthHelper.getCurrentUser() reads that same
    // SecurityContext instead of this controller re-parsing the same header/token a second time.
    @GetMapping("/me")
    public ResponseEntity<?> getUserDetails() {
        User user = authHelper.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
        UserDTO userDTO = userService.getUserDTOById(user.getId());
        return ResponseEntity.ok(userDTO);
    }
    // Demo Mode: the only write ROLE_DEMO ever needs, and only to obtain the session itself.
    // Issues a token for the single shared, seeded demo_user - never checks a password (there is
    // no legitimate way to reach this identity through /auth/login, see
    // UserService.loginByEmail). 404 while disabled, so the feature's existence isn't
    // fingerprintable when toggled off via app.demo.enabled.
    @PostMapping("/demo")
    public ResponseEntity<?> demoLogin(HttpServletRequest request) {
        if (!demoEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String clientIp = resolveClientIp(request);
        if (!rateLimiter.tryConsume("demo-issue:" + clientIp, DEMO_ISSUE_LIMIT, DEMO_ISSUE_WINDOW_MILLIS)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String token = userService.loginAsDemo();
        if (token == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(token);
    }

    // Demo Mode IP resolution for the issuance rate limiter. Render's edge does NOT sanitize a
    // client-supplied X-Forwarded-For header before forwarding it - confirmed via Render's own
    // feedback board (feedback.render.com/features/p/send-the-correct-xforwardedfor): Render
    // appends its own detected value rather than replacing whatever the client already sent, so
    // a naive "take the first entry" read (which is what Spring's ForwardedHeaderFilter /
    // request.getRemoteAddr() would do if forward-headers-strategy were enabled) would trust an
    // attacker-controlled value, letting a spoofed header mint a fresh rate-limit bucket on every
    // request. The LAST entry, in contrast, is always the value appended by whichever hop is
    // directly in front of this app (Render's edge, or Cloudflare if that also sits in front of
    // it per Render's own docs) - never something a client's request headers can set - so that
    // is the only entry trusted here. Falls back to the raw TCP peer address when the header is
    // absent entirely (e.g. local/dev testing with no proxy in front).
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@RequestBody PasswordResetDTO dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        passwordResetService.requestReset(dto.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@RequestBody PasswordResetDTO dto) {
        if (dto.getToken() == null || dto.getToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        passwordResetService.resetPassword(dto.getToken(), dto.getNewPassword());
        return ResponseEntity.noContent().build();
    }

}