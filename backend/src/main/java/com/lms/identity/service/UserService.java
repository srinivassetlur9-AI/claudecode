package com.lms.identity.service;

import com.lms.identity.domain.User;
import com.lms.identity.domain.UserProfile;
import com.lms.identity.repo.UserProfileRepository;
import com.lms.identity.repo.UserRepository;
import com.lms.identity.web.ApiException;
import com.lms.identity.web.Dtos.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service: profile, account settings and own sessions. */
@Service
public class UserService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final SessionService sessions;

    public UserService(UserRepository users, UserProfileRepository profiles, SessionService sessions) {
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @Transactional
    public ProfileView profile(Long userId) {
        return view(load(userId));
    }

    @Transactional
    public ProfileView updateProfile(Long userId, UpdateProfileRequest req) {
        User u = load(userId);
        String mobile = req.mobile() == null || req.mobile().isBlank() ? null : req.mobile().trim();
        if (mobile != null && !mobile.equals(u.getMobile())) {
            if (users.existsByMobile(mobile)) {
                throw ApiException.conflict("MOBILE_TAKEN", "An account with this mobile number already exists");
            }
            u.setMobile(mobile);
            u.setMobileVerified(false); // a changed number must be verified again
        } else if (mobile == null) {
            u.setMobile(null);
            u.setMobileVerified(false);
        }
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        UserProfile p = profileOf(u.getId());
        p.setHeadline(emptyToNull(req.headline()));
        p.setBio(emptyToNull(req.bio()));
        p.setAvatarUrl(emptyToNull(req.avatarUrl()));
        p.setCountry(emptyToNull(req.country()));
        p.setTimezone(emptyToNull(req.timezone()));
        return view(u);
    }

    @Transactional
    public ProfileView updateSettings(Long userId, UpdateSettingsRequest req) {
        User u = load(userId);
        u.setLanguage(req.language());
        return view(u);
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(Long userId, Long currentSessionId) {
        return sessions.active(userId).stream()
                .map(s -> new SessionView(s.getId(), s.getDeviceName(), s.getIpAddress(), s.getLastSeenAt(),
                        s.getCreatedAt(), s.getId().equals(currentSessionId)))
                .toList();
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private UserProfile profileOf(Long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return profiles.save(p);
        });
    }

    private ProfileView view(User u) {
        UserProfile p = profileOf(u.getId());
        return new ProfileView(UserView.of(u), p.getHeadline(), p.getBio(), p.getAvatarUrl(), p.getCountry(), p.getTimezone());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
