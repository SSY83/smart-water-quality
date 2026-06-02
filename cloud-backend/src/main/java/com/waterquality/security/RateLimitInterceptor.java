package com.waterquality.security;

import cn.hutool.json.JSONUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.waterquality.constant.ErrorCode;
import com.waterquality.dto.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter globalLimiter = RateLimiter.create(200.0);
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> loginLimiters = new ConcurrentHashMap<>();
    private static final double PER_USER_RATE = 20.0;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        if (!globalLimiter.tryAcquire()) {
            sendRateLimitResponse(response);
            return false;
        }

        String userId = (String) request.getAttribute("userId");
        if (userId != null) {
            RateLimiter userLimiter = userLimiters.computeIfAbsent(userId,
                    k -> RateLimiter.create(PER_USER_RATE));
            if (!userLimiter.tryAcquire()) {
                sendRateLimitResponse(response);
                return false;
            }
        }

        if (request.getRequestURI().contains("/auth/login")) {
            String ip = getClientIp(request);
            RateLimiter loginLimiter = loginLimiters.computeIfAbsent(ip,
                    k -> RateLimiter.create(10.0));
            if (!loginLimiter.tryAcquire()) {
                sendRateLimitResponse(response);
                return false;
            }
        }

        return true;
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(
                Result.error(ErrorCode.RATE_LIMITED)));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }
}
