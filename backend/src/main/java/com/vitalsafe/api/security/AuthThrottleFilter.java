package com.vitalsafe.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Limits how often one address may call the account endpoints that are open to guessing or to email flooding. */
public class AuthThrottleFilter extends OncePerRequestFilter {

    public record Rule(String path, int limitPerHour) {}

    private final List<Rule> rules;
    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    public AuthThrottleFilter(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static AuthThrottleFilter withDefaults(int multiplier) {
        int m = Math.max(multiplier, 1);
        return new AuthThrottleFilter(List.of(
                new Rule("/api/v1/auth/forgot-password", 5 * m),
                new Rule("/api/v1/auth/reset-password", 10 * m),
                new Rule("/api/v1/account/password", 10 * m),
                new Rule("/api/v1/account/delete", 10 * m)));
    }

    private Rule ruleFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        return rules.stream().filter(rule -> path.startsWith(rule.path())).findFirst().orElse(null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ruleFor(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = ruleFor(request);
        // Within the first hour of the first call, every call from the same address is counted
        String key = rule.path() + "|" + request.getRemoteAddr();
        int calls = counts.get(key, k -> new AtomicInteger()).incrementAndGet();
        if (calls > rule.limitPerHour()) {
            response.setStatus(429);
            response.setHeader("Retry-After", "3600");
            response.setContentType("application/json");
            response.getOutputStream().write("{\"error\":\"Too many attempts. Try again later.\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}