package com.lms.commerce.payment;

import com.lms.identity.config.LmsProperties;
import com.lms.identity.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Razorpay REST integration (https://razorpay.com/docs/api/). No SDK: three calls and HMAC checks are all we need. */
@Component
public class RazorpayGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private static final String BASE = "https://api.razorpay.com/v1";

    private final LmsProperties.Razorpay cfg;
    private final RestClient http;

    public RazorpayGateway(LmsProperties props) {
        this.cfg = props.razorpay();
        this.http = RestClient.builder().baseUrl(BASE).build();
    }

    @Override
    public boolean configured() {
        return cfg != null && cfg.configured();
    }

    @Override
    public String keyId() {
        return cfg.keyId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createOrder(long amountPaise, String currency, String receipt) {
        requireConfigured();
        try {
            Map<String, Object> res = http.post().uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise, "currency", currency, "receipt", receipt))
                    .retrieve().body(Map.class);
            return String.valueOf(res.get("id"));
        } catch (RestClientException e) {
            log.error("Razorpay order creation failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "The payment gateway could not create the order. Try again.");
        }
    }

    @Override
    public boolean verifyPaymentSignature(String gatewayOrderId, String paymentId, String signature) {
        if (!configured() || gatewayOrderId == null || paymentId == null || signature == null) return false;
        return constantTimeEquals(hmac(gatewayOrderId + "|" + paymentId, cfg.keySecret()), signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (cfg == null || cfg.webhookSecret() == null || cfg.webhookSecret().isBlank() || signature == null) return false;
        return constantTimeEquals(hmac(rawBody, cfg.webhookSecret()), signature);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String refund(String paymentId, long amountPaise, String note) {
        requireConfigured();
        try {
            Map<String, Object> res = http.post().uri("/payments/{id}/refund", paymentId)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise, "notes", Map.of("reason", note == null ? "" : note)))
                    .retrieve().body(Map.class);
            return String.valueOf(res.get("id"));
        } catch (RestClientException e) {
            log.error("Razorpay refund failed for payment {}", paymentId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "The payment gateway rejected the refund. Try again.");
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "Online payments are not configured on this server yet.");
        }
    }

    private String basicAuth() {
        String raw = cfg.keyId() + ":" + cfg.keySecret();
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
