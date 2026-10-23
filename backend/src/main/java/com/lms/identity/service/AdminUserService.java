package com.lms.identity.service;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.domain.*;
import com.lms.identity.repo.UserProfileRepository;
import com.lms.identity.repo.UserRepository;
import com.lms.identity.security.AuthPrincipal;
import com.lms.identity.security.AuthorizationService;
import com.lms.identity.security.Hashing;
import com.lms.identity.security.ResourceRef;
import com.lms.identity.web.ApiException;
import com.lms.identity.web.Dtos.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration, scoped by what the caller's roles allow (SUPER_ADMIN: everyone, ORG_ADMIN: own organization). */
@Service
public class AdminUserService {
    /** Roles an Organization Admin may hand out inside their own organization. */
    private static final Set<Role> ORG_ASSIGNABLE = EnumSet.of(Role.LEARNER, Role.INSTRUCTOR, Role.ORG_ADMIN);

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AuthorizationService authz;
    private final SessionService sessions;
    private final TokenService tokens;
    private final NotificationSender notifier;
    private final PasswordEncoder encoder;
    private final LmsProperties props;

    public AdminUserService(UserRepository users, UserProfileRepository profiles, AuthorizationService authz,
                            SessionService sessions, TokenService tokens, NotificationSender notifier,
                            PasswordEncoder encoder, LmsProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.authz = authz;
        this.sessions = sessions;
        this.tokens = tokens;
        this.notifier = notifier;
        this.encoder = encoder;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PageView<UserView> list(AuthPrincipal caller, String q, int page, int size) {
        // ORG scope is limited to the caller's organization; users with ALL scope see everyone.
        Long orgFilter;
        if (authz.can(caller, "user:read", ResourceRef.any())) {
            orgFilter = null;
        } else if (authz.can(caller, "user:read", new ResourceRef("user", null, caller.organizationId(), null))) {
            orgFilter = caller.organizationId();
        } else {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
        }
        var result = users.search(orgFilter, blankToNull(q),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("id")));
        return new PageView<>(result.map(UserView::of).getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserView get(AuthPrincipal caller, Long id) {
        User u = load(id);
        require(caller, "user:read", u);
        return UserView.of(u);
    }

    @Transactional
    public UserView setRoles(AuthPrincipal caller, Long id, Set<Role> roles) {
        User u = load(id);
        require(caller, "role:assign", u);
        boolean superAdmin = caller.roles().contains(Role.SUPER_ADMIN);
        if (!superAdmin && !ORG_ASSIGNABLE.containsAll(roles)) {
            throw ApiException.forbidden("ROLE_NOT_ASSIGNABLE", "You cannot assign one or more of these roles");
        }
        if (!superAdmin && !ORG_ASSIGNABLE.containsAll(u.roles())) {
            throw ApiException.forbidden("ROLE_NOT_ASSIGNABLE", "You cannot change the roles of this user");
        }
        if (id.equals(caller.userId()) && caller.roles().contains(Role.SUPER_ADMIN) && !roles.contains(Role.SUPER_ADMIN)) {
            throw ApiException.badRequest("SELF_DEMOTION", "You cannot remove your own Super Admin role");
        }
        u.replaceRoles(roles);
        sessions.revokeAll(u.getId(), "ADMIN"); // tokens carry roles; force re-login so changes apply immediately
        return UserView.of(u);
    }

    @Transactional
    public UserView setStatus(AuthPrincipal caller, Long id, UserStatus status) {
        User u = load(id);
        require(caller, "user:manage", u);
        if (id.equals(caller.userId())) {
            throw ApiException.badRequest("SELF_CHANGE", "You cannot change your own status");
        }
        if (status == UserStatus.PENDING) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Status can only be set to ACTIVE or DISABLED");
        }
        u.setStatus(status);
        if (status == UserStatus.DISABLED) sessions.revokeAll(u.getId(), "ADMIN");
        return UserView.of(u);
    }

    /** Creates an account with no usable password and e-mails an invite (a password-reset link). */
    @Transactional
    public UserView create(AuthPrincipal caller, CreateUserRequest req) {
        boolean superAdmin = caller.roles().contains(Role.SUPER_ADMIN);
        Long orgId = superAdmin ? req.organizationId() : caller.organizationId();
        require(caller, "user:manage", new ResourceRef("user", null, orgId, null));
        if (!superAdmin && !ORG_ASSIGNABLE.containsAll(req.roles())) {
            throw ApiException.forbidden("ROLE_NOT_ASSIGNABLE", "You cannot assign one or more of these roles");
        }
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User u = new User();
        u.setEmail(email);
        u.setFirstName(req.firstName().trim());
        u.setLastName(req.lastName().trim());
        u.setOrganizationId(orgId);
        u.setPasswordHash(encoder.encode(Hashing.randomToken(32)));
        u.setStatus(UserStatus.ACTIVE);
        u.replaceRoles(req.roles());
        users.saveAndFlush(u);
        UserProfile p = new UserProfile();
        p.setUserId(u.getId());
        profiles.save(p);

        String raw = tokens.issueLinkToken(u.getId(), TokenPurpose.PASSWORD_RESET);
        notifier.sendPasswordReset(u, props.frontendBaseUrl() + "/reset-password?token=" + raw);
        return UserView.of(u);
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessionsOf(AuthPrincipal caller, Long id) {
        User u = load(id);
        if (!id.equals(caller.userId())
                && !authz.can(caller, "session:read", ResourceRef.user(u.getId(), u.getOrganizationId()))) {
            require(caller, "user:read", u);
        }
        return sessions.active(id).stream()
                .map(s -> new SessionView(s.getId(), s.getDeviceName(), s.getIpAddress(), s.getLastSeenAt(), s.getCreatedAt(), false))
                .toList();
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private void require(AuthPrincipal caller, String action, User target) {
        require(caller, action, ResourceRef.user(target.getId(), target.getOrganizationId()));
    }

    private void require(AuthPrincipal caller, String action, ResourceRef ref) {
        if (!authz.can(caller, action, ref)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this resource");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
