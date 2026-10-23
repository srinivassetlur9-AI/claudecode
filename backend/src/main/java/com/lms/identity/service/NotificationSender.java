package com.lms.identity.service;

import com.lms.identity.domain.User;

/**
 * Delivery of verification and reset secrets. The default implementation only logs, which is fine for
 * development. Provide your own bean (SMTP, SES, SMS gateway) in production; it replaces the default.
 */
public interface NotificationSender {
    void sendEmailVerification(User user, String link);
    void sendMobileOtp(User user, String otp);
    void sendPasswordReset(User user, String link);
}
