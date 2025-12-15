package com.ktb.chatapp.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class CustomBearerTokenResolver implements BearerTokenResolver {
    
    private static final String CUSTOM_HEADER = "x-auth-token";
    
    @Override
    public String resolve(HttpServletRequest request) {
        log.info("=== CustomBearerTokenResolver.resolve ===");
        log.info("Request URI: {}", request.getRequestURI());
        log.info("Request Method: {}", request.getMethod());
        log.info("Request RemoteAddr: {}", request.getRemoteAddr());
        
        // 1. Try custom header first (x-auth-token)
        String token = request.getHeader(CUSTOM_HEADER);
        log.info("x-auth-token header: {}", token != null ? (token.length() > 20 ? token.substring(0, 20) + "..." : token) : "null");
        if (StringUtils.hasText(token)) {
            log.info("Token found in x-auth-token header");
            return token;
        }
        
        // 2. Try query parameter (for WebSocket connections)
        token = request.getParameter("token");
        log.info("token query parameter: {}", token != null ? (token.length() > 20 ? token.substring(0, 20) + "..." : token) : "null");
        if (StringUtils.hasText(token)) {
            log.info("Token found in query parameter");
            return token;
        }
        
        // 3. Try standard Authorization header (Bearer scheme)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        log.info("Authorization header: {}", authHeader != null ? (authHeader.length() > 30 ? authHeader.substring(0, 30) + "..." : authHeader) : "null");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            log.info("Token found in Authorization header");
            return token;
        }
        
        log.warn("No token found in request for URI: {}", request.getRequestURI());
        return null;
    }
}
