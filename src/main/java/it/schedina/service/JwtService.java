package it.schedina.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@ApplicationScoped
public class JwtService {

    @ConfigProperty(name = "app.jwt.secret")
    String secret;

    @ConfigProperty(name = "app.jwt.expiration-hours", defaultValue = "24")
    long expirationHours;

    private SecretKey key;

    @PostConstruct
    void init() {
        // Ensure the key is at least 256 bits for HS256
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad to 32 bytes
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationHours * 3600_000L))
                .signWith(key)
                .compact();
    }

    /** Returns the userId from a valid token, or null if invalid/expired. */
    public Long extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (JwtException | NumberFormatException e) {
            return null;
        }
    }
}
