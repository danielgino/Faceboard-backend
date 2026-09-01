package org.example.apimywebsite.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

// L-OOP4: plain component-scanned bean instead of a manually-written @Bean factory method
// (previously in WebConfig, now removed - it did nothing but call `new JwtUtil(secret)`). Spring
// resolves the single constructor automatically; @Value supplies the same property, with the
// same empty-string default, as before.
@Component
public class JwtUtil {

private final SecretKey secretKey;
private final byte[] fingerprintKey;

    // "pwdFp-v1" domain-separates this derived key from the JWT signing key itself - both are
    // ultimately rooted in the same JWT_SECRET (no new environment secret needed), but the two
    // HMAC-SHA256 operations (signing the token vs. fingerprinting the password hash) never
    // share the literal key bytes, which is standard key-hygiene practice even though the two
    // uses aren't known to interfere with each other.
    private static final byte[] FINGERPRINT_KEY_CONTEXT = "pwdFp-v1".getBytes(StandardCharsets.UTF_8);

    public JwtUtil(@Value("${JWT_SECRET:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set (no insecure default is used); " +
                            "the application refuses to start without it.");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
        this.fingerprintKey = hmacSha256(secretBytes, FINGERPRINT_KEY_CONTEXT);
    }

    // SEC-005 fix: embeds a keyed fingerprint of the password hash that was current at issuance
    // time as a custom claim. isTokenValid/matchesCurrentPassword recomputes this fingerprint
    // from the user's CURRENT password hash on every request; if the password has since changed
    // (via either normal change or reset - both write the same `password` column), the
    // fingerprints no longer match and the token stops authenticating, without waiting for its
    // natural 1-hour expiry. Deliberately reuses the existing password column as the sole source
    // of truth instead of adding a new persisted field/timestamp: no schema change, and both
    // invalidation triggers (change/reset) are covered automatically since neither needs to know
    // anything about JWTs. The token itself is signed (HS256), so a holder of an old token
    // cannot forge a new fingerprint value without the server's secret key.
    //
    // Security review follow-up: the fingerprint is HMAC-SHA256(fingerprintKey, passwordHash),
    // not a bare SHA-256(passwordHash). A JWT payload is base64, not secret, so pwdFp is
    // readable by anyone holding the token; a plain hash of a secret value exposed to a reader
    // is exactly the shape of thing that should be a keyed MAC rather than a bare digest, as a
    // matter of hygiene, even without a concrete break (bcrypt output is high-entropy and
    // per-user-salted, so an unkeyed SHA-256 of it isn't dictionary/rainbow-table-attackable
    // either way) - keying it costs nothing and removes the question entirely.
    public String generateToken(String username, String currentPasswordHash) {
        return Jwts.builder()
                .subject(username)
                .claim("pwdFp", passwordFingerprint(currentPasswordHash))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000)) //hour token
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    // Keyed HMAC-SHA256 of the bcrypt hash, never the bcrypt hash itself.
    private String passwordFingerprint(String passwordHash) {
        byte[] mac = hmacSha256(fingerprintKey, passwordHash.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is a mandatory JDK algorithm and the key is never empty (JWT_SECRET is
            // required non-blank at construction) - unreachable in practice.
            throw new IllegalStateException(e);
        }
    }

    // Returns false (rather than throwing) whenever the claim is absent, stale, or not valid
    // base64 (e.g. a token issued before this fingerprinting existed at all) - a one-time forced
    // re-login for every currently active session, not just ones with a changed password.
    // Comparison is on the raw MAC bytes via MessageDigest.isEqual, which is documented as safe
    // against timing side-channels, rather than String.equals's short-circuiting comparison.
    public boolean matchesCurrentPassword(String token, String currentPasswordHash) {
        String tokenFingerprint = parseToken(token).get("pwdFp", String.class);
        if (tokenFingerprint == null) {
            return false;
        }
        byte[] tokenFingerprintBytes;
        try {
            tokenFingerprintBytes = Base64.getUrlDecoder().decode(tokenFingerprint);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] expectedFingerprintBytes = hmacSha256(fingerprintKey, currentPasswordHash.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(tokenFingerprintBytes, expectedFingerprintBytes);
    }


public Claims parseToken(String token) {
    return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}

public boolean isTokenValid(String token, String username) {
    Claims claims = parseToken(token);
    return claims.getSubject().equals(username) && claims.getExpiration().after(new Date());
}

public String extractUsername(String token) {
    return parseToken(token).getSubject();
}

}