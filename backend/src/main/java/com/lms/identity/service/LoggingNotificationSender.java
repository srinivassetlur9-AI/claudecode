package com.lms.identity.service;

import com.lms.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development-only sender: writes the link/OTP to the log so the flow can be completed without a mail server.
 * Disabled under the "prod" profile, where a real NotificationSender bean (SMTP, SES, SMS gateway) must be supplied.
 */
@Component
@Profile("!prod")
@ConditionalOnExpression("'${spring.mail.host:}' == ''") // SmtpNotificationSender takes over once SMTP is configured
public class LoggingNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void sendEmailVerification(User user, String link) {
        log.info("[DEV MAIL] Verify email for {}: {}", user.getEmail(), link);
    }

    @Override
    public void sendMobileOtp(User user, String otp) {
        log.info("[DEV SMS] OTP for {} ({}): {}", user.getEmail(), user.getMobile(), otp);
    }

    @Override
    public void sendPasswordReset(User user, String link) {
        log.info("[DEV MAIL] Password reset for {}: {}", user.getEmail(), link);
    }
}
