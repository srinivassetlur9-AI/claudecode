import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import Checkout from './Checkout.jsx';
import PageTitle from './PageTitle.jsx';
import { STATUS_CHIP, dateTime, money } from './format.js';

export default function Orders() {
  const [courses, setCourses] = useState([]);
  const [orders, setOrders] = useState([]);
  const [owned, setOwned] = useState(new Set());
  const [selected, setSelected] = useState(null);
  const [done, setDone] = useState('');
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    api('/commerce/courses', { auth: false }).then(setCourses).catch(setError);
    api('/commerce/orders').then(setOrders).catch(setError);
    api('/me/entitlements').then((e) => setOwned(new Set(e.filter((x) => x.type === 'COURSE').map((x) => x.resourceRef)))).catch(() => {});
  }, []);
  useEffect(load, [load]);

  return (
    <>
      <PageTitle name="My Orders" />
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{done}</Alert>

      {selected && (
        <Checkout item={selected} onCancel={() => setSelected(null)}
                  onPaid={(o) => { setSelected(null); setDone(`Payment confirmed for ${o.title} (order ${o.orderNo}). An invoice was issued.`); load(); }} />
      )}

      <div className="card">
        <h2>Buy a course</h2>
        <table>
          <tbody>
            {courses.map((c) => (
              <tr key={c.courseRef}>
                <td>{c.title}</td>
                <td>{money(c.pricePaise, c.currency)}</td>
                <td style={{ textAlign: 'right' }}>
                  {owned.has(c.courseRef)
                    ? <span className="chip green">Owned</span>
                    : <button className="btn small" onClick={() => { setDone(''); setSelected({ type: 'COURSE', ref: c.courseRef, title: c.title, price: c.pricePaise, currency: c.currency }); }}>Buy</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2>Order history</h2>
        {orders.length === 0 ? <p className="muted">No orders yet.</p> : (
          <table>
            <thead><tr><th>Order</th><th>Item</th><th>Total</th><th>Status</th><th>Date</th></tr></thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td>{o.orderNo}</td>
                  <td>{o.title}{o.couponCode && <span className="muted"> · {o.couponCode}</span>}</td>
                  <td>{money(o.totalPaise, o.currency)}</td>
                  <td><span className={`chip ${STATUS_CHIP[o.status] || ''}`}>{o.status}</span></td>
                  <td>{dateTime(o.paidAt || o.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
