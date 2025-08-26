package org.example.apimywebsite.service;


import org.example.apimywebsite.api.model.PasswordResetToken;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.configuration.PasswordPolicy;
import org.example.apimywebsite.repository.PasswordResetTokenRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.example.apimywebsite.util.Constants.CLIENT_SIDE_SERVER;

@Service
public class PasswordResetService {
    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder encoder;
    private final MailService mail;

    @Value(CLIENT_SIDE_SERVER)
    private String frontendBaseUrl;

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    public PasswordResetService(UserRepository userRepo, PasswordResetTokenRepository tokenRepo,
                                PasswordEncoder encoder, MailService mail) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.encoder = encoder;
        this.mail = mail;
    }
    @Transactional
    public void requestReset(String email) {
        Optional.ofNullable(userRepo.findByEmail(email)).ifPresent(user -> {
            tokenRepo.deleteByUserId(user.getId());
            String rawToken = generateToken();
            String tokenHash = sha256(rawToken);
            PasswordResetToken prt = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .build();

            tokenRepo.save(prt);;
            prt.setUser(user);
            prt.setTokenHash(tokenHash);
            prt.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            tokenRepo.save(prt);

            String url = frontendBaseUrl + "/reset-password?token=" +
                    URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
            String body = """
                    Hi %s,

                    Click the link below to reset your password (valid for 30 minutes):
                    %s

                    If you didn't request this, ignore this email.
                    """.formatted(user.getFullName(), url);

            mail.send(user.getEmail(), "Reset your password", body);
        });
    }
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, PasswordPolicy.MESSAGE);
        }
        String tokenHash = sha256(rawToken);
        PasswordResetToken prt = tokenRepo
                .findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        User user = prt.getUser();
        user.setPassword(encoder.encode(newPassword));
        userRepo.save(user);

        prt.setUsedAt(LocalDateTime.now());
        tokenRepo.save(prt);
        tokenRepo.deleteByUserId(user.getId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return B64URL.encodeToString(bytes);
    }

    private static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}