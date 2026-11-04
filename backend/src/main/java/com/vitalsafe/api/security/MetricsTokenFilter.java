package com.vitalsafe.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Guards /actuator/prometheus with a shared token. A user's access token does not open it, and with no token
 * configured the endpoint stays closed. Deliberately NOT a @Component: SecurityConfig creates it.
 */
public class MetricsTokenFilter extends OncePerRequestFilter {

    private static final String PATH = "/actuator/prometheus";

    private final byte[] expectedDigest;   // null means the endpoint is closed

    public MetricsTokenFilter(String token) {
        if (token == null || token.isBlank()) {
            this.expectedDigest = null;
        } else if (token.length() < 32) {
            throw new IllegalStateException("METRICS_TOKEN must be at least 32 characters");
        } else {
            this.expectedDigest = sha256(token);
        }
    }

    // startsWith also covers variants such as a trailing slash or ;parameters
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean allowed = expectedDigest != null
                && header != null
                && header.startsWith("Bearer ")
                // Compares digests in constant time, so timing reveals nothing about the token
                && MessageDigest.isEqual(expectedDigest, sha256(header.substring(7)));
        if (allowed) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}