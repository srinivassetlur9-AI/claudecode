package com.lms.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.commerce.payment.PaymentGateway;
import com.lms.identity.domain.Role;
import com.lms.identity.domain.User;
import com.lms.identity.domain.UserStatus;
import com.lms.identity.repo.UserRepository;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = com.lms.identity.IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CommerceFlowTest.StubGateway.class)
class CommerceFlowTest {
    static final String PW = "Learner@12345678";
    static final String EMAIL = "commerce.learner@example.com";

    /** Stands in for Razorpay: no network, any signature that equals "valid-sig" verifies. */
    @TestConfiguration
    static class StubGateway {
        static final AtomicInteger SEQ = new AtomicInteger();

        @Bean @Primary
        PaymentGateway stubGateway() {
            return new PaymentGateway() {
                public boolean configured() { return true; }
                public String keyId() { return "rzp_test_stub"; }
                public String createOrder(long amountPaise, String currency, String receipt) { return "order_stub_" + SEQ.incrementAndGet(); }
                public boolean verifyPaymentSignature(String orderId, String paymentId, String sig) { return "valid-sig".equals(sig); }
                public boolean verifyWebhookSignature(String body, String sig) { return "valid-sig".equals(sig); }
                public String refund(String paymentId, long amountPaise, String note) { return "rfnd_stub"; }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void devicesPurchasesEntitlementsAndRefunds() throws Exception {
        User u = new User();
        u.setEmail(EMAIL);
        u.setFirstName("Commerce");
        u.setLastName("Learner");
        u.setPasswordHash(encoder.encode(PW));
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.replaceRoles(Set.of(Role.LEARNER));
        users.save(u);

        // pricing is public
        mvc.perform(get("/api/v1/commerce/plans")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/v1/commerce/orders")).andExpect(status().isUnauthorized());

        // ---- two-device limit ----
        assertThat(login("device-a-0001", true).has("accessToken")).isTrue();
        String tokenA = token;
        login("device-b-0002", true);
        String tokenB = token;
        mvc.perform(withDevice(post("/api/v1/identity/auth/login"), "device-c-0003")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", EMAIL, "password", PW))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_LIMIT_REACHED"));
        mvc.perform(withDevice(post("/api/v1/identity/auth/login"), null) // a learner must identify the device
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", EMAIL, "password", PW))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEVICE_ID_REQUIRED"));

        JsonNode list = body(mvc.perform(auth(get("/api/v1/commerce/devices"), tokenA, "device-a-0001")).andExpect(status().isOk()));
        assertThat(list.get("activeCount").asInt()).isEqualTo(2);
        assertThat(list.get("maxDevices").asInt()).isEqualTo(2);
        long deviceB = 0;
        for (JsonNode d : list.get("devices")) if (!d.get("current").asBoolean()) deviceB = d.get("id").asLong();

        // validation API
        mvc.perform(auth(post("/api/v1/commerce/devices/validate"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.reason").value("OK"));
        mvc.perform(auth(post("/api/v1/commerce/devices/validate"), tokenA, "device-z-9999"))
                .andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.reason").value("DEVICE_NOT_REGISTERED"));

        // replacing device B frees the slot, kills B's session, and lets device C in
        mvc.perform(auth(post("/api/v1/commerce/devices/" + deviceB + "/replace"), tokenA, "device-a-0001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REPLACED"));
        mvc.perform(auth(get("/api/v1/commerce/devices"), tokenB, "device-b-0002")).andExpect(status().isUnauthorized());
        login("device-c-0003", true);
        JsonNode history = body(mvc.perform(auth(get("/api/v1/commerce/devices/history"), tokenA, "device-a-0001")).andExpect(status().isOk()));
        assertThat(history.toString()).contains("REGISTERED", "BLOCKED", "REPLACED");

        // ---- course purchase with a coupon ----
        mvc.perform(auth(post("/api/v1/commerce/coupons/validate"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "welcome10", "itemType", "COURSE", "itemRef", "gen-ai-everyone"))))
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.totalPaise").value(53910));

        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=gen-ai-everyone"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(false));

        JsonNode checkout = body(mvc.perform(auth(post("/api/v1/commerce/orders"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("itemType", "COURSE", "itemRef", "gen-ai-everyone", "couponCode", "WELCOME10"))))
                .andExpect(status().isCreated()));
        assertThat(checkout.get("paymentRequired").asBoolean()).isTrue();
        assertThat(checkout.get("amount").asLong()).isEqualTo(53910); // the server priced it, not the browser
        long orderId = checkout.get("order").get("id").asLong();

        // a wrong signature does not unlock anything
        mvc.perform(auth(post("/api/v1/commerce/orders/" + orderId + "/confirm"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("razorpayPaymentId", "pay_1", "razorpaySignature", "forged"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=gen-ai-everyone"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(false));

        JsonNode paid = body(mvc.perform(auth(post("/api/v1/commerce/orders/" + orderId + "/confirm"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("razorpayPaymentId", "pay_1", "razorpaySignature", "valid-sig"))))
                .andExpect(status().isOk()));
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("invoiceId").isNull()).isFalse();
        assertThat(paid.get("refundable").asBoolean()).isTrue();
        // confirming twice does not fulfil twice
        mvc.perform(auth(post("/api/v1/commerce/orders/" + orderId + "/confirm"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("razorpayPaymentId", "pay_1", "razorpaySignature", "valid-sig"))))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=gen-ai-everyone"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=machine-learning"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(auth(get("/api/v1/commerce/invoices"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].amountPaise").value(53910));
        mvc.perform(auth(get("/api/v1/identity/me/entitlements"), tokenA, "device-a-0001")) // Module 1 sees Module 2's grants
                .andExpect(jsonPath("$[0].resourceRef").value("gen-ai-everyone"));

        // already owned, and the coupon is single-use
        mvc.perform(auth(post("/api/v1/commerce/orders"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("itemType", "COURSE", "itemRef", "gen-ai-everyone"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_OWNED"));
        mvc.perform(auth(post("/api/v1/commerce/coupons/validate"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", "WELCOME10", "itemType", "COURSE", "itemRef", "machine-learning"))))
                .andExpect(jsonPath("$.valid").value(false));

        // ---- refund: learner asks, only finance/admin can decide ----
        JsonNode refund = body(mvc.perform(auth(post("/api/v1/commerce/orders/" + orderId + "/refund"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "Wrong course"))))
                .andExpect(status().isCreated()));
        long refundId = refund.get("id").asLong();
        mvc.perform(auth(post("/api/v1/commerce/admin/refunds/" + refundId + "/approve"), tokenA, "device-a-0001"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/commerce/admin/devices/events"), tokenA, "device-a-0001")).andExpect(status().isForbidden());

        String admin = loginAdmin("super.admin@lms.local");
        mvc.perform(auth(post("/api/v1/commerce/admin/refunds/" + refundId + "/approve"), admin, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSED"));
        mvc.perform(auth(post("/api/v1/commerce/admin/refunds/" + refundId + "/approve"), admin, null))
                .andExpect(status().isConflict());
        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=gen-ai-everyone"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(false)); // the refund took the access back
        mvc.perform(auth(get("/api/v1/commerce/orders/" + orderId), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(auth(get("/api/v1/commerce/admin/devices/events"), admin, null))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.eventType=='BLOCKED')]").isNotEmpty());

        // ---- plan: Annual unlocks the whole library ----
        JsonNode planCheckout = body(mvc.perform(auth(post("/api/v1/commerce/orders"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("itemType", "PLAN", "itemRef", "ANNUAL"))))
                .andExpect(status().isCreated()));
        assertThat(planCheckout.get("amount").asLong()).isEqualTo(249900);
        mvc.perform(auth(post("/api/v1/commerce/orders/" + planCheckout.get("order").get("id").asLong() + "/confirm"), tokenA, "device-a-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("razorpayPaymentId", "pay_2", "razorpaySignature", "valid-sig"))))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/commerce/subscription"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.planCode").value("ANNUAL")).andExpect(jsonPath("$.free").value(false));
        mvc.perform(auth(get("/api/v1/commerce/entitlements/check?type=COURSE&ref=machine-learning"), tokenA, "device-a-0001"))
                .andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(auth(post("/api/v1/commerce/orders"), tokenA, "device-a-0001").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("itemType", "PLAN", "itemRef", "ENTERPRISE"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTACT_SALES"));

        // webhook: unsigned calls are refused
        mvc.perform(post("/api/v1/commerce/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    // ---- helpers ----

    private String token;

    private JsonNode login(String deviceKey, boolean expectOk) throws Exception {
        ResultActions a = mvc.perform(withDevice(post("/api/v1/identity/auth/login"), deviceKey)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", EMAIL, "password", PW))));
        if (expectOk) a.andExpect(status().isOk());
        JsonNode n = body(a);
        token = n.path("accessToken").asText(null);
        return n;
    }

    private String loginAdmin(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/identity/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "Demo@12345678")))).andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    private static MockHttpServletRequestBuilder withDevice(MockHttpServletRequestBuilder b, String key) {
        return key == null ? b : b.header("X-Device-Id", key);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token, String deviceKey) {
        b.header("Authorization", "Bearer " + token);
        return withDevice(b, deviceKey);
    }

    private JsonNode body(ResultActions a) throws Exception {
        return json.readTree(a.andReturn().getResponse().getContentAsString());
    }
}
