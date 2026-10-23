package com.lms.identity.security;

/**
 * What an action is being performed on. organizationId / ownerId let the permission check apply
 * ORG and OWN scopes; leave them null when unknown (ORG/OWN grants then deny).
 */
public record ResourceRef(String type, Long id, Long organizationId, Long ownerId) {
    public static ResourceRef any() {
        return new ResourceRef(null, null, null, null);
    }

    public static ResourceRef user(Long id, Long organizationId) {
        return new ResourceRef("user", id, organizationId, id);
    }
}
