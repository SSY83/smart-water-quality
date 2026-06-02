package com.waterquality.service;

import cn.hutool.core.util.StrUtil;
import com.waterquality.constant.ErrorCode;
import com.waterquality.dto.LoginRequest;
import com.waterquality.dto.Result;
import com.waterquality.entity.User;
import com.waterquality.exception.BusinessException;
import com.waterquality.mapper.UserMapper;
import com.waterquality.security.JwtBlacklist;
import com.waterquality.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklist jwtBlacklist;

    private final Map<String, AtomicInteger> loginFailCount = new ConcurrentHashMap<>();
    private final Map<String, Long> accountLocked = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, JwtBlacklist jwtBlacklist) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtBlacklist = jwtBlacklist;
    }

    public Result<Map<String, String>> login(LoginRequest request) {
        Long lockedUntil = accountLocked.get(request.getUsername());
        if (lockedUntil != null && System.currentTimeMillis() < lockedUntil) {
            long remainingMin = (lockedUntil - System.currentTimeMillis()) / 60000 + 1;
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "账号已锁定，请" + remainingMin + "分钟后重试");
        }

        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            recordLoginFailure(request.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!user.isValid()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFailure(request.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 缓存用户信息到Redis
        Map<String, String> sessionInfo = new HashMap<>();
        sessionInfo.put("userId", String.valueOf(user.getId()));
        sessionInfo.put("username", user.getUsername());
        sessionInfo.put("role", user.getRole());
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set("session:" + user.getId(), sessionInfo, 24, TimeUnit.HOURS);
        }

        // 登录成功，清除失败计数
        loginFailCount.remove(request.getUsername());
        accountLocked.remove(request.getUsername());

        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return Result.success(result);
    }

    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            long expiration = jwtTokenProvider.getExpiration(token);
            jwtBlacklist.blacklist(token, expiration);
            Long userId = jwtTokenProvider.getUserId(token);
            if (redisTemplate != null) {
                redisTemplate.delete("session:" + userId);
            }
        }
        return Result.success(null);
    }

    private void recordLoginFailure(String username) {
        int attempts = loginFailCount.computeIfAbsent(username, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            accountLocked.put(username, System.currentTimeMillis() + LOCK_DURATION_MS);
            loginFailCount.remove(username);
        }
    }

    public boolean hasPermission(Long userId, Long pointId) {
        User user = userMapper.selectById(userId);
        if (user == null) return false;
        if (user.isAdmin()) return true;

        // 普通用户需要检查权限关联表
        // 此处简化处理，实际应查询 user_point_permission 表
        return true;
    }

    public boolean hasRole(Long userId, String requiredRole) {
        User user = userMapper.selectById(userId);
        if (user == null) return false;
        if ("admin".equals(user.getRole())) return true;
        if ("user".equals(user.getRole()) && !"admin".equals(requiredRole)) return true;
        return user.getRole().equals(requiredRole);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
