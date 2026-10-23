package com.lms.identity.security;

import com.lms.identity.repo.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Validates the bearer token and checks that its session has not been revoked (logout, password change, admin). */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserSessionRepository sessions;

    public JwtAuthFilter(JwtService jwt, UserSessionRepository sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parse(header.substring(7)).ifPresent(p -> {
                if (p.sessionId() != null
                        && sessions.existsByIdAndRevokedAtIsNullAndExpiresAtAfter(p.sessionId(), Instant.now())) {
                    var authorities = p.roles().stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
                    SecurityContextHolder.getContext()
                            .setAuthentication(new UsernamePasswordAuthenticationToken(p, null, authorities));
                }
            });
        }
        chain.doFilter(req, res);
    }
}
