package com.waterquality.filter;

import cn.hutool.json.JSONUtil;
import com.waterquality.constant.ErrorCode;
import com.waterquality.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Component
@Order(2)
public class SecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    // SQL injection patterns
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        Pattern.compile("(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|TRUNCATE|EXEC|EXECUTE)\\b\\s)",
                Pattern.CASE_INSENSITIVE),
        Pattern.compile("('\\s*OR\\s+'1'?\\s*=\\s*'1)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("('\\s*OR\\s+\\d+\\s*=\\s*\\d+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(--|#|/\\*|\\*/)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(;\\s*(SELECT|INSERT|UPDATE|DELETE|DROP))", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(<script.*?>.*?</script>)", Pattern.CASE_INSENSITIVE),
    };

    // XSS patterns
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<\\s*script\\b[^>]*>(.*?)<\\s*/\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\bon\\w+\\s*=\\s*\"?[^\"]*\")", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\s*iframe\\b", Pattern.CASE_INSENSITIVE),
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Skip check for static resources and WebSocket
        String path = request.getRequestURI();
        if (path.equals("/") || path.equals("/index.html")
                || path.startsWith("/ws-test.html") || path.startsWith("/ws/")
                || path.startsWith("/actuator/")
                || path.endsWith(".js") || path.endsWith(".css")
                || path.endsWith(".png") || path.endsWith(".ico")
                || path.endsWith(".svg") || path.endsWith(".woff")
                || path.endsWith(".woff2")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check query parameters
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (String value : values) {
                    if (detectSqlInjection(name) || detectSqlInjection(value)) {
                        log.warn("检测到SQL注入攻击: IP={}, path={}, param={}, value={}",
                                getClientIp(request), path, name, value);
                        sendBlockResponse(response);
                        return;
                    }
                    if (detectXss(value)) {
                        log.warn("检测到XSS攻击: IP={}, path={}, param={}, value={}",
                                getClientIp(request), path, name, value);
                        sendBlockResponse(response);
                        return;
                    }
                }
            }
        }

        // Wrap request with sanitized parameters for non-GET requests
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(new SanitizedRequestWrapper(request), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean detectSqlInjection(String value) {
        if (value == null || value.isEmpty()) return false;
        for (Pattern p : SQL_INJECTION_PATTERNS) {
            if (p.matcher(value).find()) return true;
        }
        return false;
    }

    private boolean detectXss(String value) {
        if (value == null || value.isEmpty()) return false;
        for (Pattern p : XSS_PATTERNS) {
            if (p.matcher(value).find()) return true;
        }
        return false;
    }

    private void sendBlockResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(
                Result.error(ErrorCode.FORBIDDEN)));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }

    /**
     * Request wrapper that sanitizes XSS content from parameters
     */
    private static class SanitizedRequestWrapper extends HttpServletRequestWrapper {

        public SanitizedRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return sanitize(super.getParameter(name));
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            Map<String, String[]> sanitized = new HashMap<>(original.size());
            for (Map.Entry<String, String[]> entry : original.entrySet()) {
                String[] values = entry.getValue();
                String[] cleanValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    cleanValues[i] = sanitize(values[i]);
                }
                sanitized.put(entry.getKey(), cleanValues);
            }
            return Collections.unmodifiableMap(sanitized);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] cleanValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanValues[i] = sanitize(values[i]);
            }
            return cleanValues;
        }

        private String sanitize(String value) {
            if (value == null) return null;
            return value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#x27;");
        }
    }
}
