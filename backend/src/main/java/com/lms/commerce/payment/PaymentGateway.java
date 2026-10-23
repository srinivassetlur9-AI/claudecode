package com.lms.commerce.payment;

/**
 * Payment gateway port. Razorpay is the only implementation today; the order, refund and webhook flows depend on
 * this interface only, so another gateway (or a test double) can be swapped in without touching them.
 * UPI, cards, net banking and international cards are all handled inside the gateway's hosted checkout:
 * card and bank details never reach this server.
 */
public interface PaymentGateway {
    /** False until the gateway keys are set (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET). */
    boolean configured();

    /** Public key the browser checkout needs. */
    String keyId();

    /** Creates a payable order at the gateway and returns the gateway's order id. */
    String createOrder(long amountPaise, String currency, String receipt);

    /** Verifies the signature the browser returns after a successful payment. */
    boolean verifyPaymentSignature(String gatewayOrderId, String paymentId, String signature);

    /** Verifies the signature on a server-to-server webhook call. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /** Refunds a captured payment and returns the gateway's refund id. */
    String refund(String paymentId, long amountPaise, String note);
}
