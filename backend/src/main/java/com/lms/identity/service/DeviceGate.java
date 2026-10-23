package com.lms.identity.service;

import com.lms.identity.domain.User;

/**
 * Hook for Module 2 (device management). When a bean implementing this exists, login and token refresh
 * consult it; when none exists nothing is enforced.
 */
public interface DeviceGate {
    /**
     * Called after the password and account state are verified. Returns the id of the registered device the new
     * session should be bound to (or null when the user is not subject to device limits), or throws an
     * ApiException to refuse the login (for example when the device limit is reached).
     */
    Long admit(User user, SessionService.ClientInfo client);

    /** False once the device has been deactivated or replaced, so its sessions can no longer be refreshed. */
    boolean isActive(Long deviceId);
}
