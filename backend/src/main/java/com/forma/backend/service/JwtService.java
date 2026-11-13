package com.forma.backend.service;

import com.forma.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
        @Value("${app.jwt.secret:}") String secret,
        @Value("${app.jwt.expiration-ms:2592000000}") long expirationMs
    ) {
        byte[] keyBytes;
        if (secret != null && secret.trim().length() >= 32) {
            keyBytes = secret.trim().getBytes(StandardCharsets.UTF_8);
        } else {
            keyBytes = new byte[64];
            new SecureRandom().nextBytes(keyBytes);
            log.warn("[Security] JWT_SECRET ausente ou fraco; chave HMAC efêmera de 512 bits gerada para esta instância.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        return generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
    }

    public String generateToken(UUID userId, String email) {
        return generateToken(userId, email, 1);
    }

    public String generateToken(UUID userId, String email, int tokenVersion) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("tv", tokenVersion)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public int extractTokenVersion(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            Integer tv = claims.get("tv", Integer.class);
            return tv != null ? tv : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public String getEmailFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.get("email", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return !"ADMIN".equals(claims.get("role", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public String generateAdminToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 86400000L * 7); // 7 dias de validade
        return Jwts.builder()
            .subject("admin_" + username)
            .claim("role", "ADMIN")
            .claim("username", username)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact();
    }

    public boolean validateAdminToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return "ADMIN".equals(claims.get("role", String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
