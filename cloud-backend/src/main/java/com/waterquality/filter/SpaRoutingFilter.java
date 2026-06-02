package com.waterquality.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaRoutingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Don't intercept API, actuator, WebSocket, or file requests
        if (path.startsWith("/api/") || path.startsWith("/actuator/")
                || path.startsWith("/ws/") || path.contains(".")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Forward non-file, non-API GET requests to index.html (SPA client routes)
        request.getRequestDispatcher("/index.html").forward(request, response);
    }
}
