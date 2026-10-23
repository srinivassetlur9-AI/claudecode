package com.lms.identity.web;

import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.security.AuthorizationService;
import com.lms.identity.security.ResourceRef;
import com.lms.identity.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Contracts other modules rely on. Call these with the user's bearer token:
 * <ul>
 *   <li>GET  /auth/validate - who is this token? (user id, organization id, roles)</li>
 *   <li>POST /authz/can     - can this token's user perform an action on a resource?</li>
 * </ul>
 * Entitlements and device/session lists are served from /me/** and /users/{id}/**.
 */
@RestController
@RequestMapping("/api/v1/identity")
public class ContractController {
    private final AuthorizationService authz;

    public ContractController(AuthorizationService authz) {
        this.authz = authz;
    }

    @GetMapping("/auth/validate")
    public TokenValidation validate(@AuthenticationPrincipal AuthPrincipal me) {
        return new TokenValidation(me.userId(), me.organizationId(), me.roles(), me.sessionId());
    }

    @PostMapping("/authz/can")
    public AuthzResponse can(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AuthzRequest req) {
        ResourceRef ref = new ResourceRef(req.resourceType(), req.resourceId(), req.organizationId(), req.ownerId());
        return new AuthzResponse(authz.can(me, req.action(), ref));
    }
}
