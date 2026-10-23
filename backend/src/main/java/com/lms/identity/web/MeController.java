package com.lms.identity.web;

import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.service.EntitlementProvider;
import com.lms.identity.service.SessionService;
import com.lms.identity.service.UserService;
import com.lms.identity.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/identity/me")
public class MeController {
    private final UserService users;
    private final SessionService sessions;
    private final EntitlementProvider entitlements;

    public MeController(UserService users, SessionService sessions, EntitlementProvider entitlements) {
        this.users = users;
        this.sessions = sessions;
        this.entitlements = entitlements;
    }

    @GetMapping
    public ProfileView me(@AuthenticationPrincipal AuthPrincipal me) {
        return users.profile(me.userId());
    }

    @PutMapping
    public ProfileView updateProfile(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody UpdateProfileRequest req) {
        return users.updateProfile(me.userId(), req);
    }

    @PutMapping("/settings")
    public ProfileView updateSettings(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody UpdateSettingsRequest req) {
        return users.updateSettings(me.userId(), req);
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal AuthPrincipal me) {
        return users.sessions(me.userId(), me.sessionId());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        sessions.revoke(me.userId(), id, "LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAll(@AuthenticationPrincipal AuthPrincipal me) {
        sessions.revokeAll(me.userId(), "LOGOUT");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/entitlements")
    public List<EntitlementView> entitlements(@AuthenticationPrincipal AuthPrincipal me) {
        return entitlements.entitlementsFor(me.userId());
    }
}
