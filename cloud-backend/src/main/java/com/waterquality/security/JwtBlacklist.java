package com.waterquality.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class JwtBlacklist {

    private final Cache<String, Long> blacklist;

    public JwtBlacklist() {
        this.blacklist = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(24, TimeUnit.HOURS)
                .build();
    }

    public void blacklist(String token, long expirationMillis) {
        long ttl = Math.max(expirationMillis - System.currentTimeMillis(), 60000);
        blacklist.put(hashToken(token), System.currentTimeMillis() + ttl);
    }

    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.getIfPresent(hashToken(token));
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            blacklist.invalidate(hashToken(token));
            return false;
        }
        return true;
    }

    private String hashToken(String token) {
        int len = Math.min(token.length(), 32);
        return token.substring(token.length() - len);
    }

    public long size() {
        return blacklist.estimatedSize();
    }
}
