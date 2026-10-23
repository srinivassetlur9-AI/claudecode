package com.lms.identity.security;

import static com.lms.identity.domain.Role.*;
import static com.lms.identity.security.AuthorizationService.Scope.*;

import com.lms.identity.domain.Role;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * can(user, action, resource): the permission contract every module uses.
 * The role -> action -> scope matrix lives here. Later modules add their actions to it
 * (for example course:manage for CONTENT_ADMIN) instead of inventing their own checks.
 */
@Service
public class AuthorizationService {
    public enum Scope { ALL, ORG, OWN }

    private static final Map<Role, Map<String, Scope>> MATRIX = new EnumMap<>(Role.class);

    static {
        MATRIX.put(SUPER_ADMIN, Map.ofEntries(
                Map.entry("*", ALL)));
        MATRIX.put(ORG_ADMIN, Map.of(
                "user:read", ORG, "user:manage", ORG, "role:assign", ORG, "org:manage", ORG,
                "course:read", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(SUPPORT_AGENT, Map.of(
                "user:read", ALL, "session:read", ALL, "course:read", ALL, "device:manage", ALL, "device:read", ALL,
                "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(CONTENT_ADMIN, Map.of(
                "course:read", ALL, "course:manage", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(EXAM_ADMIN, Map.of(
                "course:read", ALL, "exam:manage", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(EVENT_ADMIN, Map.of(
                "course:read", ALL, "event:manage", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(FINANCE_ADMIN, Map.of(
                "finance:manage", ALL, "device:read", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(EVALUATOR, Map.of(
                "course:read", ALL, "evaluation:grade", ALL, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(INSTRUCTOR, Map.of(
                "course:read", ALL, "course:author", OWN, "profile:read", OWN, "profile:update", OWN));
        MATRIX.put(LEARNER, Map.of(
                "course:read", ALL, "profile:read", OWN, "profile:update", OWN));
    }

    public boolean can(AuthPrincipal p, String action, ResourceRef resource) {
        for (Role role : p.roles()) {
            Map<String, Scope> grants = MATRIX.get(role);
            if (grants == null) continue;
            Scope scope = grants.getOrDefault(action, grants.get("*"));
            if (scope != null && inScope(scope, p, resource)) return true;
        }
        return false;
    }

    private boolean inScope(Scope scope, AuthPrincipal p, ResourceRef r) {
        return switch (scope) {
            case ALL -> true;
            case ORG -> p.organizationId() != null && Objects.equals(p.organizationId(), r.organizationId());
            case OWN -> p.userId() != null && Objects.equals(p.userId(), r.ownerId());
        };
    }
}
