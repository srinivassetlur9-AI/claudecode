import { post } from '../api.js';

let scriptPromise = null;

/** Razorpay's hosted checkout. It collects UPI / card / net banking / international card details, never our server. */
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve();
  scriptPromise ??= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload = resolve;
    s.onerror = () => { scriptPromise = null; reject(new Error('Could not load the payment window. Check your connection and try again.')); };
    document.body.appendChild(s);
  });
  return scriptPromise;
}

/**
 * Creates the order (the server prices it), opens Razorpay Checkout if anything is payable, then asks the server to
 * verify the payment. Resolves with the paid order; rejects with an Error whose message can be shown to the learner.
 */
export async function purchase({ itemType, itemRef, couponCode }) {
  const co = await post('/commerce/orders', { itemType, itemRef, couponCode: couponCode || null });
  if (!co.paymentRequired) return co.order; // fully discounted: already paid and fulfilled

  await loadRazorpay();
  return new Promise((resolve, reject) => {
    let settled = false;
    const done = (fn, v) => { if (!settled) { settled = true; fn(v); } };
    const rzp = new window.Razorpay({
      key: co.keyId,
      amount: co.amount,
      currency: co.currency,
      name: co.name,
      description: co.description,
      order_id: co.razorpayOrderId,
      prefill: { name: co.prefillName, email: co.prefillEmail },
      theme: { color: '#2457e6' },
      handler: async (r) => {
        try {
          done(resolve, await post(`/commerce/orders/${co.order.id}/confirm`, {
            razorpayPaymentId: r.razorpay_payment_id,
            razorpaySignature: r.razorpay_signature,
          }));
        } catch (e) { done(reject, e); }
      },
      modal: { ondismiss: () => done(reject, new Error('Payment was cancelled. You have not been charged.')) },
    });
    rzp.on('payment.failed', (resp) => done(reject, new Error(resp.error?.description || 'The payment failed. Please try again.')));
    rzp.open();
  });
}
