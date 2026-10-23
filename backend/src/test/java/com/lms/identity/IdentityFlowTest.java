package com.lms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.identity.domain.Role;
import com.lms.identity.domain.User;
import com.lms.identity.service.NotificationSender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityFlowTest.Capture.class)
class IdentityFlowTest {
    static final String DEMO_PASSWORD = "Demo@12345678";

    @TestConfiguration
    static class Capture {
        @Bean @Primary
        NotificationSender capturingSender() {
            return new NotificationSender() {
                public void sendEmailVerification(User u, String link) { SENT.add(new String[]{"verify", u.getEmail(), link}); }
                public void sendMobileOtp(User u, String otp) { SENT.add(new String[]{"otp", u.getEmail(), otp}); }
                public void sendPasswordReset(User u, String link) { SENT.add(new String[]{"reset", u.getEmail(), link}); }
            };
        }
    }

    static final List<String[]> SENT = new CopyOnWriteArrayList<>();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registrationToLoginToResetEndToEnd() throws Exception {
        String email = "asha.learner@example.com";
        String pw = "Str0ngPassword1";

        post("/api/v1/identity/auth/register", Map.of("accountType", "LEARNER", "email", email, "password", pw,
                "firstName", "Asha", "lastName", "Rao")).andExpect(status().isCreated());

        // not usable until the email is verified
        post("/api/v1/identity/auth/login", Map.of("email", email, "password", pw))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = lastToken("verify", email);
        post("/api/v1/identity/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
        post("/api/v1/identity/auth/verify-email", Map.of("token", token)) // single use
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        JsonNode tokens = login(email, pw);
        String access = tokens.get("accessToken").asText();
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value("LEARNER"));
        mvc.perform(get("/api/v1/identity/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        // forgot -> reset: old sessions die, new password works
        post("/api/v1/identity/auth/forgot-password", Map.of("email", email)).andExpect(status().isOk());
        String reset = lastToken("reset", email);
        post("/api/v1/identity/auth/reset-password", Map.of("token", reset, "newPassword", "An0therStrongPass"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        post("/api/v1/identity/auth/login", Map.of("email", email, "password", pw)).andExpect(status().isUnauthorized());
        login(email, "An0therStrongPass");
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
        post("/api/v1/identity/auth/forgot-password", Map.of("email", "nobody@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokensRotateAndReuseRevokesEverything() throws Exception {
        JsonNode first = login("learner@lms.local", DEMO_PASSWORD);
        String r1 = first.get("refreshToken").asText();

        JsonNode second = body(post("/api/v1/identity/auth/refresh", Map.of("refreshToken", r1)).andExpect(status().isOk()));
        String r2 = second.get("refreshToken").asText();
        assertThat(r2).isNotEqualTo(r1);

        post("/api/v1/identity/auth/refresh", Map.of("refreshToken", r1)).andExpect(status().isUnauthorized()); // replay
        post("/api/v1/identity/auth/refresh", Map.of("refreshToken", r2)).andExpect(status().isUnauthorized()); // family revoked
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        JsonNode t = login("evaluator@lms.local", DEMO_PASSWORD);
        post("/api/v1/identity/auth/logout", Map.of("refreshToken", t.get("refreshToken").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/identity/me").header("Authorization", "Bearer " + t.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            post("/api/v1/identity/auth/login", Map.of("email", "finance.admin@lms.local", "password", "wrong-Password1"))
                    .andExpect(status().isUnauthorized());
        }
        post("/api/v1/identity/auth/login", Map.of("email", "finance.admin@lms.local", "password", DEMO_PASSWORD))
                .andExpect(status().isLocked());
    }

    @Test
    void everyRoleCanLogInAndSeesOnlyWhatItsPermissionsAllow() throws Exception {
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase().replace('_', '.') + "@lms.local";
            if (role == Role.FINANCE_ADMIN) continue; // locked by the lockout test
            JsonNode t = login(email, DEMO_PASSWORD);
            String auth = "Bearer " + t.get("accessToken").asText();

            mvc.perform(get("/api/v1/identity/auth/validate").header("Authorization", auth))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value(role.name()));

            boolean canListUsers = role == Role.SUPER_ADMIN || role == Role.ORG_ADMIN || role == Role.SUPPORT_AGENT;
            mvc.perform(get("/api/v1/identity/users").header("Authorization", auth))
                    .andExpect(canListUsers ? status().isOk() : status().isForbidden());
        }
    }

    @Test
    void orgAdminOnlySeesOwnOrganization() throws Exception {
        JsonNode t = login("org.admin@lms.local", DEMO_PASSWORD);
        JsonNode page = body(mvc.perform(get("/api/v1/identity/users").header("Authorization", "Bearer " + t.get("accessToken").asText()))
                .andExpect(status().isOk()));
        assertThat(page.get("items")).isNotEmpty();
        page.get("items").forEach(u -> assertThat(u.get("organizationId").asLong()).isEqualTo(t.get("user").get("organizationId").asLong()));
    }

    @Test
    void otherModulesCanProtectEndpointsWithTheContracts() throws Exception {
        JsonNode t = login("content.admin@lms.local", DEMO_PASSWORD);
        String auth = "Bearer " + t.get("accessToken").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/identity/authz/can").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("action", "course:manage"))))
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/identity/authz/can").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("action", "finance:manage"))))
                .andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(get("/api/v1/identity/me/entitlements").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/identity/me/sessions").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].current").value(true));
        mvc.perform(get("/api/v1/identity/auth/validate")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions post(String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode login(String email, String password) throws Exception {
        return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/identity/auth/login").header("X-Device-Id", "identity-test-device").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", password)))).andExpect(status().isOk()));
    }

    private JsonNode body(ResultActions a) throws Exception {
        return json.readTree(a.andReturn().getResponse().getContentAsString());
    }

    private String lastToken(String kind, String email) {
        for (int i = SENT.size() - 1; i >= 0; i--) {
            String[] s = SENT.get(i);
            if (s[0].equals(kind) && s[1].equals(email)) {
                return s[2].substring(s[2].indexOf("token=") + 6);
            }
        }
        throw new AssertionError("no " + kind + " message for " + email);
    }
}
