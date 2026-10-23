import { useCallback, useEffect, useState } from 'react';
import { api, post, put } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { STATUS_CHIP, dateTime, money } from './format.js';

const FINANCE = ['SUPER_ADMIN', 'FINANCE_ADMIN'];
const DEVICE = ['SUPER_ADMIN', 'FINANCE_ADMIN', 'SUPPORT_AGENT'];

/** Finance and support tools. The server checks every call; the tabs shown here only mirror those permissions. */
export default function CommerceAdmin() {
  const { user } = useAuth();
  const has = (list) => user.roles.some((r) => list.includes(r));
  const tabs = [
    ...(has(FINANCE) ? [['refunds', 'Refunds'], ['coupons', 'Coupons'], ['orders', 'Orders'], ['plans', 'Plans']] : []),
    ...(has(DEVICE) ? [['devices', 'Device audit']] : []),
  ];
  const [tab, setTab] = useState(tabs[0]?.[0]);

  return (
    <>
      <h1>Billing &amp; Devices</h1>
      <div className="tabs" role="tablist">
        {tabs.map(([k, label]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>
      {tab === 'refunds' && <Refunds />}
      {tab === 'coupons' && <CouponsAdmin />}
      {tab === 'orders' && <OrdersAdmin />}
      {tab === 'plans' && <PlansAdmin />}
      {tab === 'devices' && <DeviceAudit canReset={has(DEVICE)} />}
    </>
  );
}

function useList(path) {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState(null);
  const load = useCallback(() => api(path).then(setRows).catch(setError), [path]);
  useEffect(() => { load(); }, [load]);
  return [rows, load, error, setError];
}

function Refunds() {
  const [rows, load, error, setError] = useList('/commerce/admin/refunds');
  async function decide(id, action) {
    const note = window.prompt(action === 'approve' ? 'Note for the learner (optional)' : 'Why is it rejected? (shown to the learner)') ?? null;
    if (note === null) return;
    setError(null);
    try { await post(`/commerce/admin/refunds/${id}/${action}`, { note }); load(); } catch (e) { setError(e); }
  }
  return (
    <div className="card">
      <h2>Refund requests</h2>
      <Alert>{error && errorText(error)}</Alert>
      <table>
        <thead><tr><th>Order</th><th>Learner</th><th>Amount</th><th>Reason</th><th>Status</th><th /></tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>{r.orderNo}</td><td>#{r.userId}</td><td>{money(r.amountPaise)}</td><td>{r.reason}</td>
              <td><span className={`chip ${STATUS_CHIP[r.status] || ''}`}>{r.status}</span></td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {r.status === 'REQUESTED' && <>
                  <button className="btn small" onClick={() => decide(r.id, 'approve')}>Approve &amp; refund</button>{' '}
                  <button className="btn danger small" onClick={() => decide(r.id, 'reject')}>Reject</button>
                </>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <p className="muted">No refund requests.</p>}
    </div>
  );
}

function CouponsAdmin() {
  const [rows, load, error, setError] = useList('/commerce/admin/coupons');
  const [f, setF] = useState({ code: '', discountType: 'PERCENT', discountValue: 10, appliesTo: 'ALL', maxRedemptions: '' });

  async function create(e) {
    e.preventDefault();
    setError(null);
    try {
      const value = f.discountType === 'FLAT' ? Math.round(Number(f.discountValue) * 100) : Number(f.discountValue); // flat amounts are typed in rupees, stored as paise
      await post('/commerce/admin/coupons', { ...f, discountValue: value, maxRedemptions: f.maxRedemptions ? Number(f.maxRedemptions) : null });
      setF({ ...f, code: '' });
      load();
    } catch (err) { setError(err); }
  }
  async function toggle(c) {
    try { await put(`/commerce/admin/coupons/${c.id}/active`, { active: !c.active }); load(); } catch (e) { setError(e); }
  }

  return (
    <>
      <form className="card" onSubmit={create}>
        <h2>New coupon</h2>
        <div className="row">
          <div><label htmlFor="cc">Code</label><input id="cc" required maxLength={40} pattern="[A-Za-z0-9_\-]+" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></div>
          <div><label htmlFor="ct">Type</label><select id="ct" value={f.discountType} onChange={(e) => setF({ ...f, discountType: e.target.value })}><option value="PERCENT">Percent off</option><option value="FLAT">Flat amount (₹)</option></select></div>
        </div>
        <div className="row">
          <div><label htmlFor="cv">{f.discountType === 'PERCENT' ? 'Percent (1-100)' : 'Rupees off'}</label><input id="cv" type="number" min="1" max={f.discountType === 'PERCENT' ? 100 : undefined} required value={f.discountValue} onChange={(e) => setF({ ...f, discountValue: e.target.value })} /></div>
          <div><label htmlFor="ca">Applies to</label><select id="ca" value={f.appliesTo} onChange={(e) => setF({ ...f, appliesTo: e.target.value })}><option value="ALL">Plans and courses</option><option value="PLAN">Plans only</option><option value="COURSE">Courses only</option></select></div>
        </div>
        <label htmlFor="cm">Maximum uses (blank = unlimited)</label>
        <input id="cm" type="number" min="1" value={f.maxRedemptions} onChange={(e) => setF({ ...f, maxRedemptions: e.target.value })} style={{ maxWidth: 200 }} />
        <Alert>{error && errorText(error)}</Alert>
        <button className="btn" style={{ marginTop: '1rem' }}>Create coupon</button>
      </form>
      <div className="card">
        <h2>Coupons</h2>
        <table>
          <thead><tr><th>Code</th><th>Discount</th><th>Applies to</th><th>Used</th><th>Status</th><th /></tr></thead>
          <tbody>
            {rows.map((c) => (
              <tr key={c.id}>
                <td>{c.code}</td><td>{c.discountType === 'PERCENT' ? `${c.discountValue}%` : money(c.discountValue)}</td><td>{c.appliesTo}</td>
                <td>{c.redeemedCount}{c.maxRedemptions ? ` / ${c.maxRedemptions}` : ''}</td>
                <td><span className={`chip ${c.active ? 'green' : 'red'}`}>{c.active ? 'Active' : 'Off'}</span></td>
                <td style={{ textAlign: 'right' }}><button className="btn ghost small" onClick={() => toggle(c)}>{c.active ? 'Turn off' : 'Turn on'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function OrdersAdmin() {
  const [rows, , error] = useList('/commerce/admin/orders');
  return (
    <div className="card">
      <h2>Recent orders</h2>
      <Alert>{error && errorText(error)}</Alert>
      <table>
        <thead><tr><th>Order</th><th>Learner</th><th>Item</th><th>Total</th><th>Status</th><th>Date</th></tr></thead>
        <tbody>
          {rows.map((o) => (
            <tr key={o.id}><td>{o.orderNo}</td><td>#{o.userId}</td><td>{o.title}</td><td>{money(o.totalPaise, o.currency)}</td>
              <td><span className={`chip ${STATUS_CHIP[o.status] || ''}`}>{o.status}</span></td><td>{dateTime(o.paidAt || o.createdAt)}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PlansAdmin() {
  const [rows, load, error, setError] = useList('/commerce/admin/plans');
  async function edit(p) {
    const input = window.prompt(`New price for ${p.name} in rupees`, String(p.pricePaise / 100));
    if (input === null) return;
    const rupees = Number(input);
    if (!Number.isFinite(rupees) || rupees < 0) { setError({ message: 'Enter a valid amount' }); return; }
    setError(null);
    try { await put(`/commerce/admin/plans/${p.id}`, { pricePaise: Math.round(rupees * 100) }); load(); } catch (e) { setError(e); }
  }
  async function toggle(p) {
    try { await put(`/commerce/admin/plans/${p.id}`, { active: !p.active }); load(); } catch (e) { setError(e); }
  }
  return (
    <div className="card">
      <h2>Plans &amp; pricing</h2>
      <Alert>{error && errorText(error)}</Alert>
      <table>
        <thead><tr><th>Plan</th><th>Type</th><th>Price</th><th>Duration</th><th>Status</th><th /></tr></thead>
        <tbody>
          {rows.map((p) => (
            <tr key={p.id}>
              <td>{p.name}</td><td>{p.planType}</td><td>{p.selfServe ? money(p.pricePaise, p.currency) : 'Contact sales'}</td>
              <td>{p.durationDays ? `${p.durationDays} days` : '—'}</td>
              <td><span className={`chip ${p.active ? 'green' : 'red'}`}>{p.active ? 'Active' : 'Hidden'}</span></td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                {p.selfServe && p.planType !== 'FREE' && <button className="btn ghost small" onClick={() => edit(p)}>Change price</button>}{' '}
                {p.planType !== 'FREE' && <button className="btn ghost small" onClick={() => toggle(p)}>{p.active ? 'Hide' : 'Show'}</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DeviceAudit({ canReset }) {
  const [userId, setUserId] = useState('');
  const [events, setEvents] = useState([]);
  const [devices, setDevices] = useState(null);
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);

  const search = useCallback(async (id) => {
    setError(null);
    try {
      setEvents(await api(`/commerce/admin/devices/events${id ? `?userId=${id}` : ''}`));
      setDevices(id ? await api(`/commerce/admin/users/${id}/devices`) : null);
    } catch (e) { setError(e); }
  }, []);
  useEffect(() => { search(''); }, [search]);

  async function reset() {
    if (!window.confirm(`Deactivate every device of learner #${userId}? They will be signed out everywhere.`)) return;
    try {
      const r = await post(`/commerce/admin/users/${userId}/devices/reset`, {});
      setMsg(`${r.deactivated} device(s) deactivated.`);
      search(userId);
    } catch (e) { setError(e); }
  }

  return (
    <div className="card">
      <h2>Device audit</h2>
      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setMsg(''); search(userId.trim()); }}>
        <input placeholder="Learner user id (blank = everyone)" value={userId} onChange={(e) => setUserId(e.target.value)} inputMode="numeric" />
        <button className="btn ghost">Search</button>
      </form>
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{msg}</Alert>
      {devices && (
        <>
          <p>{devices.activeCount} of {devices.maxDevices} slots in use. {canReset && devices.activeCount > 0 && <button className="btn danger small" onClick={reset}>Reset all devices</button>}</p>
          <table>
            <thead><tr><th>Device</th><th>Status</th><th>Last active</th></tr></thead>
            <tbody>{devices.devices.map((d) => <tr key={d.id}><td>{d.name}</td><td><span className={`chip ${STATUS_CHIP[d.status] || ''}`}>{d.status}</span></td><td>{dateTime(d.lastSeenAt)}</td></tr>)}</tbody>
          </table>
        </>
      )}
      <table style={{ marginTop: '1rem' }}>
        <thead><tr><th>When</th><th>Learner</th><th>Event</th><th>Detail</th><th>IP</th></tr></thead>
        <tbody>
          {events.map((e) => (
            <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td>#{e.userId}</td>
              <td><span className={`chip ${e.eventType === 'BLOCKED' ? 'red' : ''}`}>{e.eventType}</span></td><td>{e.detail || '—'}</td><td>{e.ipAddress || '—'}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
