package org.wodrol.brakoffpc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class MobileBearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final String mobileToken;

    public MobileBearerTokenFilter(@Value("${app.security.mobile.token}") String mobileToken) {
        this.mobileToken = mobileToken == null ? "" : mobileToken.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        if ("/api/health".equals(path)) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization == null || authorization.isBlank();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing bearer token");
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!matchesConfiguredToken(token)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "mobile-api",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MOBILE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private boolean matchesConfiguredToken(String token) {
        if (mobileToken.isBlank() || token.isBlank()) {
            return false;
        }
        byte[] configured = mobileToken.getBytes(StandardCharsets.UTF_8);
        byte[] provided = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configured, provided);
    }
}
