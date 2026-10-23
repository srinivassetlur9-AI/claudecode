package com.lms.identity.config;

import com.lms.identity.domain.*;
import com.lms.identity.repo.OrganizationRepository;
import com.lms.identity.repo.UserProfileRepository;
import com.lms.identity.repo.UserRepository;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run setup. Creates the initial Super Admin from BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD,
 * and (only when SEED_DEMO_USERS=true) one active demo user per role so every role can be tried out.
 */
@Component
public class BootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);
    static final String DEMO_PASSWORD = "Demo@12345678";

    private final LmsProperties props;
    private final UserRepository users;
    private final OrganizationRepository orgs;
    private final UserProfileRepository profiles;
    private final PasswordEncoder encoder;

    public BootstrapRunner(LmsProperties props, UserRepository users, OrganizationRepository orgs,
                           UserProfileRepository profiles, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.orgs = orgs;
        this.profiles = profiles;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var b = props.bootstrap();
        Organization platform = orgs.findFirstByType("PLATFORM").orElse(null);

        if (b.adminEmail() != null && !b.adminEmail().isBlank() && b.adminPassword() != null && !b.adminPassword().isBlank()) {
            create(b.adminEmail(), b.adminPassword(), "Super", "Admin", platform == null ? null : platform.getId(), Role.SUPER_ADMIN);
        }
        if (b.seedDemoUsers()) {
            log.warn("SEED_DEMO_USERS is on: creating demo users with a well-known password. Never enable this in production.");
            Organization demoOrg = new Organization();
            demoOrg.setName("Demo Organization");
            demoOrg.setType("ORGANIZATION");
            Long demoOrgId = null;
            for (Role r : Role.values()) {
                if (r == Role.SUPER_ADMIN) continue;
                boolean orgScoped = r == Role.ORG_ADMIN;
                if (orgScoped && demoOrgId == null && !users.existsByEmailIgnoreCase(email(r))) {
                    demoOrgId = orgs.save(demoOrg).getId();
                }
                create(email(r), DEMO_PASSWORD, "Demo", pretty(r), orgScoped ? demoOrgId : null, r);
            }
            create(email(Role.SUPER_ADMIN), DEMO_PASSWORD, "Demo", pretty(Role.SUPER_ADMIN),
                    platform == null ? null : platform.getId(), Role.SUPER_ADMIN);
        }
    }

    private void create(String email, String password, String first, String last, Long orgId, Role role) {
        if (users.existsByEmailIgnoreCase(email)) return;
        User u = new User();
        u.setEmail(email.trim().toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setFirstName(first);
        u.setLastName(last);
        u.setOrganizationId(orgId);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.replaceRoles(Set.of(role));
        users.save(u);
        UserProfile p = new UserProfile();
        p.setUserId(u.getId());
        profiles.save(p);
        log.info("Created {} user {}", role, u.getEmail());
    }

    static String email(Role r) {
        return r.name().toLowerCase().replace('_', '.') + "@lms.local";
    }

    private static String pretty(Role r) {
        String[] parts = r.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
