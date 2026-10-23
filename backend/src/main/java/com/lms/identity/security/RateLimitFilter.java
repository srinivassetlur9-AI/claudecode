package com.lms.identity.security;

import com.lms.identity.config.LmsProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window limiter per client IP + endpoint for login, OTP/verification and password-reset endpoints.
 * In-memory, so it is per instance; put a shared limiter (gateway / Redis) in front when running several replicas.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS = 60_000;
    private static final Set<String> LIMITED = Set.of(
            "/api/v1/identity/auth/login", "/api/v1/identity/auth/register",
            "/api/v1/identity/auth/verify-email", "/api/v1/identity/auth/verify-mobile",
            "/api/v1/identity/auth/resend-verification", "/api/v1/identity/auth/forgot-password",
            "/api/v1/identity/auth/reset-password", "/api/v1/identity/auth/refresh");

    private final int limit;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(LmsProperties props) {
        this.limit = props.security().rateLimitPerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !LIMITED.contains(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(req.getRemoteAddr() + "|" + req.getRequestURI(), k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            allowed = q.size() < limit;
            if (allowed) q.addLast(now);
        }
        if (hits.size() > 10_000) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || now - e.getValue().peekLast() > WINDOW_MS;
                }
            });
        }
        if (!allowed) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType("application/json");
            res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":429,"
                    + "\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again in a minute.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
