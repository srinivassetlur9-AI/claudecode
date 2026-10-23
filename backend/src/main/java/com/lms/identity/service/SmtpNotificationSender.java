package com.lms.identity.service;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Real email delivery over SMTP, active whenever spring.mail.host is set (see MAIL_HOST in application.yml).
 * Sends asynchronously and never throws, so an SMTP outage cannot fail or roll back registration / forgot-password;
 * the user can request a new link. There is no SMS gateway wired in yet, so OTPs are still only logged
 * (and never in the prod profile).
 */
@Component
@ConditionalOnExpression("'${spring.mail.host:}' != ''")
public class SmtpNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationSender.class);

    private final JavaMailSender mail;
    private final LmsProperties props;

    public SmtpNotificationSender(JavaMailSender mail, LmsProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    @Async
    public void sendEmailVerification(User user, String link) {
        send(user, "Verify your email address", """
                Hi %s,

                Welcome! Confirm your email address to activate your account:

                %s

                This link is valid for %d hours. If you did not create an account, ignore this email.
                """.formatted(user.getFirstName(), link, props.tokens().emailVerifyHours()));
    }

    @Override
    @Async
    public void sendPasswordReset(User user, String link) {
        send(user, "Reset your password", """
                Hi %s,

                We received a request to reset your password. Choose a new one here:

                %s

                This link is valid for %d minutes and can be used once. If you did not ask for this, ignore this email.
                """.formatted(user.getFirstName(), link, props.tokens().resetMinutes()));
    }

    @Override
    public void sendMobileOtp(User user, String otp) {
        // No SMS provider configured. Never log the code in production.
        if (!props.logOtp()) {
            log.warn("Mobile OTP requested for user {} but no SMS gateway is configured", user.getId());
            return;
        }
        log.info("[DEV SMS] OTP for {} ({}): {}", user.getEmail(), user.getMobile(), otp);
    }

    private void send(User user, String subject, String body) {
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mailFrom());
            m.setTo(user.getEmail());
            m.setSubject(subject);
            m.setText(body);
            mail.send(m);
        } catch (Exception e) {
            log.error("Failed to send '{}' email to user {}", subject, user.getId(), e);
        }
    }
}
