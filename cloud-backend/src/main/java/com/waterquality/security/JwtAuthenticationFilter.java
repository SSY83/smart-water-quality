package com.waterquality.security;

import cn.hutool.json.JSONUtil;
import com.waterquality.constant.ErrorCode;
import com.waterquality.dto.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklist jwtBlacklist;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    JwtBlacklist jwtBlacklist) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtBlacklist = jwtBlacklist;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
            return false;
        }

        String token = authHeader.substring(7);
        if (jwtBlacklist.isBlacklisted(token)) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
            return false;
        }
        if (!jwtTokenProvider.validateToken(token)) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
            return false;
        }

        request.setAttribute("userId", jwtTokenProvider.getUserId(token));
        request.setAttribute("username", jwtTokenProvider.getUsername(token));
        request.setAttribute("role", jwtTokenProvider.getRole(token));
        return true;
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status,
                                    String errorCode) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(errorCode);
        response.getWriter().write(JSONUtil.toJsonStr(result));
    }
}
