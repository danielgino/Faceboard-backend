package org.example.apimywebsite.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JwtUtil's secret handling (C5 fix): the signing secret must come from a real,
 * non-blank configured value — there is no insecure hardcoded fallback, and a missing/blank
 * secret must fail the application fast rather than silently signing tokens with a known string.
 */
class JwtUtilTest {

    private static final String SECRET_A = "unit-test-secret-A-long-enough-for-HMAC256-1234567890";
    private static final String SECRET_B = "unit-test-secret-B-long-enough-for-HMAC256-0987654321";
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01";

    @Test
    void constructor_withRealSecret_generatesAndValidatesTokens() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_A);

        String token = jwtUtil.generateToken("userA", PASSWORD_HASH);

        assertTrue(jwtUtil.isTokenValid(token, "userA"));
        assertEquals("userA", jwtUtil.extractUsername(token));
    }

    @Test
    void constructor_withNullSecret_failsFastInsteadOfUsingAFallback() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtUtil(null));
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void constructor_withBlankSecret_failsFastInsteadOfUsingAFallback() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("   "));
    }

    @Test
    void constructor_withEmptySecret_failsFastInsteadOfUsingAFallback() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil(""));
    }

    @Test
    void tokensSignedWithOneSecret_areRejectedByAnInstanceUsingADifferentSecret() {
        // Proves there is no shared/implicit fallback secret: each JwtUtil instance is
        // strictly bound to the secret it was actually constructed with.
        JwtUtil signer = new JwtUtil(SECRET_A);
        JwtUtil verifier = new JwtUtil(SECRET_B);

        String token = signer.generateToken("userA", PASSWORD_HASH);

        assertThrows(Exception.class, () -> verifier.isTokenValid(token, "userA"));
    }

    // ---- SEC-005 fix: a token embeds a fingerprint of the password hash current at issuance
    // time; matchesCurrentPassword must accept it only while the user's current password hash
    // still produces that same fingerprint, and reject it the instant the password changes. ----

    @Test
    void matchesCurrentPassword_unchangedPasswordHash_returnsTrue() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_A);
        String token = jwtUtil.generateToken("userA", PASSWORD_HASH);

        assertTrue(jwtUtil.matchesCurrentPassword(token, PASSWORD_HASH));
    }

    @Test
    void matchesCurrentPassword_changedPasswordHash_returnsFalse() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_A);
        String token = jwtUtil.generateToken("userA", PASSWORD_HASH);

        String newPasswordHash = "$2a$10$zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz";
        assertFalse(jwtUtil.matchesCurrentPassword(token, newPasswordHash));
    }

    @Test
    void generateToken_neverEmbedsTheRawPasswordHashInTheTokenText() {
        // The JWT is signed, not encrypted - its payload is base64, not secret. The embedded
        // claim must be a derived fingerprint, never the actual stored credential hash verbatim.
        JwtUtil jwtUtil = new JwtUtil(SECRET_A);

        String token = jwtUtil.generateToken("userA", PASSWORD_HASH);

        assertFalse(token.contains(PASSWORD_HASH));
    }

    // Security review follow-up: pwdFp must be a keyed MAC (HMAC-SHA256 over a key derived from
    // JWT_SECRET), not a bare hash - otherwise anyone reading the JWT payload (it's base64, not
    // encrypted) could recompute the exact same "fingerprint" for a guessed/known password hash
    // with no server secret at all. Proven here by showing two JwtUtil instances configured with
    // different secrets produce DIFFERENT fingerprints for the identical password hash - a bare
    // unkeyed hash would produce the same value regardless of secret.
    @Test
    void passwordFingerprint_isKeyedByTheServerSecret_notReproducibleWithoutIt() {
        JwtUtil jwtUtilA = new JwtUtil(SECRET_A);
        JwtUtil jwtUtilB = new JwtUtil(SECRET_B);

        String tokenA = jwtUtilA.generateToken("userA", PASSWORD_HASH);
        String tokenB = jwtUtilB.generateToken("userA", PASSWORD_HASH);

        String fingerprintA = jwtUtilA.parseToken(tokenA).get("pwdFp", String.class);
        String fingerprintB = jwtUtilB.parseToken(tokenB).get("pwdFp", String.class);

        assertNotEquals(fingerprintA, fingerprintB);
    }
}
