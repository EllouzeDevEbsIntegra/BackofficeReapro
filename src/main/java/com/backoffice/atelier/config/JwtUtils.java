package com.backoffice.atelier.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${app.secret-key}")
    private String accessSecretKey;

    @Value("${app.expiration-time}")
    private long accessExpiration;

    @Value("${jwt.refresh.secret.key}")
    private String refreshSecretKey;

    @Value("${app.refresh-expiration-time}")
    private long refreshExpiration;

    private Key getKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ======== ACCESS TOKEN ========
    public String generateAccessToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setExpiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getKey(accessSecretKey))
                .compact();
    }

    public String extractEmailFromAccessToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey(accessSecretKey))
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    // ======== REFRESH TOKEN ========
    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getKey(refreshSecretKey))
                .compact();
    }

    public String extractEmailFromRefreshToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey(refreshSecretKey))
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
