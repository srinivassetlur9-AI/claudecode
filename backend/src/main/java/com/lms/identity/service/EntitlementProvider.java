package com.lms.identity.service;

import com.lms.identity.web.Dtos.EntitlementView;
import java.util.List;

/**
 * Hook for Module 2 (Subscription, Commerce &amp; Device Management).
 *
 * <p>Answers "what is this user entitled to?" (courses, plans, features). Module 1 ships the default
 * {@link DbEntitlementProvider}, which reads the empty {@code user_entitlements} table, so the answer is always
 * "nothing" until Module 2 writes rows or supplies its own bean implementing this interface.
 */
public interface EntitlementProvider {
    List<EntitlementView> entitlementsFor(Long userId);
}
