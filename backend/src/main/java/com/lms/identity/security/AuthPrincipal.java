package com.lms.identity.security;

import com.lms.identity.domain.Role;
import java.util.Set;

/** The authenticated caller, as carried in the access token. */
public record AuthPrincipal(Long userId, Long organizationId, Set<Role> roles, Long sessionId) {}
