package com.lms.identity.service;

import com.lms.identity.web.ApiException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** At least 10 characters with upper case, lower case and a digit; must not contain the email's local part. */
    public static void check(String password, String email) {
        boolean ok = password.length() >= 10 && password.length() <= 128
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!ok) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Password must be at least 10 characters and include upper case, lower case and a digit");
        }
        if (email != null) {
            String local = email.split("@")[0].toLowerCase();
            if (local.length() >= 4 && password.toLowerCase().contains(local)) {
                throw ApiException.badRequest("WEAK_PASSWORD", "Password must not contain your email name");
            }
        }
    }
}
