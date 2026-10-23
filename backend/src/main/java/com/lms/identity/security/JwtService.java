package com.lms.identity.security;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.Role;
import com.lms.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER = "lms-identity";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(LmsProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("lms.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public String issueAccessToken(User user, Long sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("org", user.getOrganizationId())
                .claim("roles", user.roles().stream().map(Enum::name).toList())
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /** Returns empty for any invalid, expired or tampered token. */
    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            Set<Role> roles = EnumSet.noneOf(Role.class);
            Object raw = c.get("roles");
            if (raw instanceof List<?> list) {
                list.forEach(r -> roles.add(Role.valueOf(String.valueOf(r))));
            }
            Number org = c.get("org", Number.class);
            Number sid = c.get("sid", Number.class);
            return Optional.of(new AuthPrincipal(Long.valueOf(c.getSubject()),
                    org == null ? null : org.longValue(), roles, sid == null ? null : sid.longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
