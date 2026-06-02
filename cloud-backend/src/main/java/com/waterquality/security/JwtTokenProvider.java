package com.waterquality.security;

import cn.hutool.core.date.DateUtil;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.*;

@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long expiration;
    private final long refreshThreshold;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.refresh-threshold}") long refreshThreshold) {
        byte[] keyBytes = Base64.getEncoder().encode(secret.getBytes());
        this.signingKey = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
        this.expiration = expiration;
        this.refreshThreshold = refreshThreshold;
    }

    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(signingKey)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new JwtException("令牌已过期", e);
        } catch (SignatureException e) {
            throw new JwtException("令牌签名无效", e);
        } catch (MalformedJwtException e) {
            throw new JwtException("令牌格式错误", e);
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getUsername(String token) {
        return (String) parseToken(token).get("username");
    }

    public String getRole(String token) {
        return (String) parseToken(token).get("role");
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public long getExpiration(String token) {
        return parseToken(token).getExpiration().getTime();
    }

    public boolean shouldRefresh(String token) {
        try {
            Claims claims = parseToken(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            return remaining > 0 && remaining < refreshThreshold;
        } catch (JwtException e) {
            return false;
        }
    }
}
