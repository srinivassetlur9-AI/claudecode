import { useState } from 'react';
import { post } from '../api.js';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { purchase } from './checkout.js';
import { money } from './format.js';

/** Order summary with a coupon box and a Pay button. item = { type: 'PLAN'|'COURSE', ref, title, price, currency }. */
export default function Checkout({ item, onPaid, onCancel }) {
  const [code, setCode] = useState('');
  const [quote, setQuote] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const total = quote?.valid ? quote.totalPaise : item.price;

  async function apply() {
    if (!code.trim()) return;
    setError(null);
    try {
      setQuote(await post('/commerce/coupons/validate', { code: code.trim(), itemType: item.type, itemRef: item.ref }));
    } catch (e) { setError(e); }
  }

  async function pay() {
    setBusy(true); setError(null);
    try {
      const order = await purchase({ itemType: item.type, itemRef: item.ref, couponCode: quote?.valid ? quote.code : null });
      onPaid(order);
    } catch (e) { setError(e); } finally { setBusy(false); }
  }

  return (
    <div className="card" style={{ borderColor: 'var(--blue)' }}>
      <h2>Checkout</h2>
      <table>
        <tbody>
          <tr><td>{item.title}</td><td style={{ textAlign: 'right' }}>{money(item.price, item.currency)}</td></tr>
          {quote?.valid && <tr><td>Coupon {quote.code}</td><td style={{ textAlign: 'right', color: 'var(--green)' }}>− {money(quote.discountPaise, item.currency)}</td></tr>}
          <tr><td><b>Total</b></td><td style={{ textAlign: 'right' }}><b>{money(total, item.currency)}</b></td></tr>
        </tbody>
      </table>

      <label htmlFor="coupon">Coupon code</label>
      <div className="toolbar" style={{ marginBottom: 0 }}>
        <input id="coupon" value={code} onChange={(e) => { setCode(e.target.value); setQuote(null); }} placeholder="e.g. WELCOME10" />
        <button type="button" className="btn ghost" onClick={apply}>Apply</button>
      </div>
      {quote && <Alert kind={quote.valid ? 'ok' : 'error'}>{quote.message}</Alert>}
      <Alert>{error && errorText(error)}</Alert>

      <div className="toolbar" style={{ marginTop: '1rem', marginBottom: 0 }}>
        <button className="btn" disabled={busy} onClick={pay}>{busy ? 'Processing…' : total === 0 ? 'Get it free' : `Pay ${money(total, item.currency)}`}</button>
        <button className="btn ghost" disabled={busy} onClick={onCancel}>Cancel</button>
      </div>
      <p className="hint">Pay with UPI, cards, net banking or an international card. Payments are handled securely by Razorpay.</p>
    </div>
  );
}
