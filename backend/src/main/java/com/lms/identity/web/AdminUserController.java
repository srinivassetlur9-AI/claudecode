package com.lms.identity.web;

import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.service.AdminUserService;
import com.lms.identity.service.EntitlementProvider;
import com.lms.identity.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Authorization is enforced in AdminUserService through AuthorizationService (role + scope), not here. */
@RestController
@RequestMapping("/api/v1/identity/users")
public class AdminUserController {
    private final AdminUserService admin;
    private final EntitlementProvider entitlements;

    public AdminUserController(AdminUserService admin, EntitlementProvider entitlements) {
        this.admin = admin;
        this.entitlements = entitlements;
    }

    @GetMapping
    public PageView<UserView> list(@AuthenticationPrincipal AuthPrincipal me,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return admin.list(me, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody CreateUserRequest req) {
        return admin.create(me, req);
    }

    @GetMapping("/{id}")
    public UserView get(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        return admin.get(me, id);
    }

    @PutMapping("/{id}/roles")
    public UserView roles(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @Valid @RequestBody SetRolesRequest req) {
        return admin.setRoles(me, id, req.roles());
    }

    @PutMapping("/{id}/status")
    public UserView status(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id, @Valid @RequestBody SetStatusRequest req) {
        return admin.setStatus(me, id, req.status());
    }

    /** Device/session list per user, so Module 2 can enforce device limits. */
    @GetMapping("/{id}/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        return admin.sessionsOf(me, id);
    }

    /** Entitlements hook for Module 2: the user themself, or someone allowed to read that user. */
    @GetMapping("/{id}/entitlements")
    public List<EntitlementView> entitlements(@AuthenticationPrincipal AuthPrincipal me, @PathVariable Long id) {
        if (!id.equals(me.userId())) {
            admin.get(me, id); // throws 403/404 unless the caller may read this user
        }
        return entitlements.entitlementsFor(id);
    }
}
