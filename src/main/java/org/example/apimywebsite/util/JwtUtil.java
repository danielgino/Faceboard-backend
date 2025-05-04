package org.example.apimywebsite.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;

public class JwtUtil {


//    private static final String SECRET = "mySuperSecretKeyThatIsLongEnoughForHMAC256!123456";
//    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes());
private final SecretKey secretKey;

///PRODCTIOUN
    public JwtUtil() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null) {
            throw new RuntimeException("JWT_SECRET not found in system properties");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }



//LOCALHOST
//    public JwtUtil() {
//        // נסה לקרוא מהסביבה, ואם לא קיים - ברירת מחדל לפיתוח מקומי
//        String secret = System.getenv("JWT_SECRET");
//
//        if (secret == null || secret.isBlank()) {
//            System.out.println("⚠️ JWT_SECRET not found in environment. Using default development key.");
//            secret = "localDevSecretKeyThatIsLongEnoughForHMAC256_123456789";
//        }
//
//        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
//    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000)) //hour token
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
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