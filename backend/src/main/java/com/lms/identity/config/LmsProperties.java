package com.lms.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lms")
public record LmsProperties(Jwt jwt, Security security, Tokens tokens, String frontendBaseUrl,
                            String corsOrigins, Bootstrap bootstrap, String mailFrom, boolean logOtp,
                            Commerce commerce, Razorpay razorpay) {
    public record Jwt(String secret, int accessTtlMinutes, int refreshTtlDays) {}
    public record Security(int maxFailedAttempts, int lockoutMinutes, int rateLimitPerMinute) {}
    public record Tokens(int emailVerifyHours, int otpMinutes, int resetMinutes) {}
    public record Commerce(int maxDevices, int refundWindowDays) {}
    public record Razorpay(String keyId, String keySecret, String webhookSecret) {
        public boolean configured() {
            return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        }
    }
    public record Bootstrap(String adminEmail, String adminPassword, boolean seedDemoUsers) {}
}
