package com.lms.commerce.web;

import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.security.AuthorizationService;
import com.lms.identity.security.ResourceRef;
import com.lms.identity.web.ApiException;
import org.springframework.stereotype.Component;

/** Module 2's use of Module 1's permission contract: can(user, action, resource). */
@Component
public class AccessGuard {
    private final AuthorizationService authz;

    public AccessGuard(AuthorizationService authz) {
        this.authz = authz;
    }

    public void require(AuthPrincipal me, String action) {
        if (!authz.can(me, action, ResourceRef.any())) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
        }
    }
}
