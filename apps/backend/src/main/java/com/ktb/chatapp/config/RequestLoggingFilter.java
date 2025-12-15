package com.ktb.chatapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * 요청 로깅 필터
 * 개발 모드에서만 요청 메서드와 URI를 로깅
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Value("${spring.profiles.active:production}")
    private String profile;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        log.info("╔═══════════════════════════════════════════════════════════════════════════════╗");
        log.info("║                           📥 INCOMING REQUEST                                ║");
        log.info("╠═══════════════════════════════════════════════════════════════════════════════╣");
        log.info("║ Method: {}", request.getMethod());
        log.info("║ URI: {}", request.getRequestURI());
        log.info("║ QueryString: {}", request.getQueryString() != null ? request.getQueryString() : "none");
        log.info("║ RemoteAddr: {}", request.getRemoteAddr());
        log.info("║ RemoteHost: {}", request.getRemoteHost());
        
        // Log all headers
        log.info("║ Headers:");
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            if (headerName.toLowerCase().contains("token") || headerName.toLowerCase().contains("auth")) {
                // Mask sensitive headers
                if (headerValue != null && headerValue.length() > 20) {
                    headerValue = headerValue.substring(0, 20) + "...";
                }
            }
            log.info("║   {}: {}", headerName, headerValue);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("║ ❌ Exception during filter chain: {}", e.getMessage(), e);
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("╠═══════════════════════════════════════════════════════════════════════════════╣");
            log.info("║                           📤 RESPONSE                                       ║");
            log.info("╠═══════════════════════════════════════════════════════════════════════════════╣");
            log.info("║ Status: {}", response.getStatus());
            log.info("║ Duration: {}ms", duration);
            log.info("╚═══════════════════════════════════════════════════════════════════════════════╝");
        }
    }
}
