import { useCallback, useEffect, useState } from 'react';
import { api, post } from '../api.js';
import { Alert, Brand, errorText } from '../components/AuthLayout.jsx';
import { STATUS_CHIP, dateOnly, dateTime, money } from './format.js';
import PageTitle from './PageTitle.jsx';

export default function Invoices() {
  const [invoices, setInvoices] = useState([]);
  const [orders, setOrders] = useState([]);
  const [refunds, setRefunds] = useState([]);
  const [open, setOpen] = useState(null);
  const [asking, setAsking] = useState(null); // order id the refund form is open for
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    api('/commerce/invoices').then(setInvoices).catch(setError);
    api('/commerce/orders').then(setOrders).catch(setError);
    api('/commerce/refunds').then(setRefunds).catch(setError);
  }, []);
  useEffect(load, [load]);

  async function requestRefund(e) {
    e.preventDefault();
    setError(null); setMsg('');
    try {
      await post(`/commerce/orders/${asking}/refund`, { reason });
      setAsking(null); setReason('');
      setMsg('Refund requested. A finance admin will review it and you will see the decision here.');
      load();
    } catch (err) { setError(err); }
  }

  const refundable = orders.filter((o) => o.refundable);

  return (
    <>
      <PageTitle name="Invoices & Refunds" />
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{msg}</Alert>

      <div className="card">
        <h2>Invoices</h2>
        {invoices.length === 0 ? <p className="muted">Invoices appear here after your first payment.</p> : (
          <table>
            <thead><tr><th>Invoice</th><th>Description</th><th>Amount</th><th>Issued</th><th /></tr></thead>
            <tbody>
              {invoices.map((i) => (
                <tr key={i.id}>
                  <td>{i.invoiceNo}</td><td>{i.description}</td><td>{money(i.amountPaise, i.currency)}</td><td>{dateOnly(i.issuedAt)}</td>
                  <td style={{ textAlign: 'right' }}><button className="btn ghost small" onClick={() => setOpen(i)}>View</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>Refunds</h2>
        {refundable.length > 0 && (
          <>
            <p className="muted" style={{ marginTop: 0 }}>These orders are still inside the refund window:</p>
            <table>
              <tbody>
                {refundable.map((o) => (
                  <tr key={o.id}>
                    <td>{o.orderNo}</td><td>{o.title}</td><td>{money(o.totalPaise, o.currency)}</td>
                    <td style={{ textAlign: 'right' }}><button className="btn danger small" onClick={() => { setAsking(o.id); setMsg(''); }}>Request refund</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
        {asking && (
          <form onSubmit={requestRefund}>
            <label htmlFor="reason">Why are you requesting a refund?</label>
            <textarea id="reason" required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            <p className="hint">If approved, access to this item is removed and the money returns to your original payment method.</p>
            <div className="toolbar" style={{ marginTop: '.75rem' }}>
              <button className="btn">Submit request</button>
              <button type="button" className="btn ghost" onClick={() => setAsking(null)}>Cancel</button>
            </div>
          </form>
        )}
        {refunds.length === 0 ? (refundable.length === 0 && <p className="muted">No refunds requested.</p>) : (
          <table style={{ marginTop: '1rem' }}>
            <thead><tr><th>Order</th><th>Amount</th><th>Status</th><th>Requested</th><th>Note</th></tr></thead>
            <tbody>
              {refunds.map((r) => (
                <tr key={r.id}>
                  <td>{r.orderNo}</td><td>{money(r.amountPaise)}</td>
                  <td><span className={`chip ${STATUS_CHIP[r.status] || ''}`}>{r.status}</span></td>
                  <td>{dateTime(r.createdAt)}</td><td>{r.decisionNote || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {open && (
        <div className="modal-bg" onClick={() => setOpen(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} id="invoice-print">
            <Brand />
            <h2 style={{ marginTop: '1rem' }}>Invoice {open.invoiceNo}</h2>
            <p className="muted">Issued {dateTime(open.issuedAt)} · Order {open.orderNo}</p>
            <p><b>Billed to</b><br />{open.billingName}<br />{open.billingEmail}</p>
            <table>
              <thead><tr><th>Description</th><th style={{ textAlign: 'right' }}>Amount</th></tr></thead>
              <tbody>
                <tr><td>{open.description}</td><td style={{ textAlign: 'right' }}>{money(open.amountPaise, open.currency)}</td></tr>
                <tr><td><b>Total paid</b></td><td style={{ textAlign: 'right' }}><b>{money(open.amountPaise, open.currency)}</b></td></tr>
              </tbody>
            </table>
            <div className="toolbar no-print" style={{ marginTop: '1rem', marginBottom: 0 }}>
              <button className="btn" onClick={() => window.print()}>Print / save as PDF</button>
              <button className="btn ghost" onClick={() => setOpen(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
