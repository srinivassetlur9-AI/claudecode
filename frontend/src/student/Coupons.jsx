import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, post } from '../api.js';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { dateTime, money } from './format.js';
import PageTitle from './PageTitle.jsx';

export default function Coupons() {
  const [items, setItems] = useState([]);
  const [mine, setMine] = useState([]);
  const [pick, setPick] = useState('');
  const [code, setCode] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([api('/commerce/plans', { auth: false }), api('/commerce/courses', { auth: false })])
      .then(([plans, courses]) => {
        const list = [
          ...plans.filter((p) => p.selfServe && p.planType !== 'FREE').map((p) => ({ key: `PLAN|${p.code}`, label: `${p.name} plan · ${money(p.pricePaise, p.currency)}` })),
          ...courses.map((c) => ({ key: `COURSE|${c.courseRef}`, label: `${c.title} · ${money(c.pricePaise, c.currency)}` })),
        ];
        setItems(list);
        setPick(list[0]?.key || '');
      }).catch(setError);
    api('/commerce/coupons/mine').then(setMine).catch(setError);
  }, []);

  async function check(e) {
    e.preventDefault();
    setError(null); setResult(null);
    const [itemType, itemRef] = pick.split('|');
    try { setResult(await post('/commerce/coupons/validate', { code, itemType, itemRef })); } catch (err) { setError(err); }
  }

  return (
    <>
      <PageTitle name="Coupons" />
      <form className="card" onSubmit={check}>
        <h2>Check a coupon</h2>
        <label htmlFor="item">Apply it to</label>
        <select id="item" value={pick} onChange={(e) => { setPick(e.target.value); setResult(null); }}>
          {items.map((i) => <option key={i.key} value={i.key}>{i.label}</option>)}
        </select>
        <label htmlFor="code">Coupon code</label>
        <input id="code" required value={code} onChange={(e) => { setCode(e.target.value); setResult(null); }} />
        <Alert>{error && errorText(error)}</Alert>
        {result && <Alert kind={result.valid ? 'ok' : 'error'}>{result.valid ? `${result.message}: you save ${money(result.discountPaise)} and pay ${money(result.totalPaise)}.` : result.message}</Alert>}
        <div className="toolbar" style={{ marginTop: '1rem', marginBottom: 0 }}>
          <button className="btn">Check</button>
          {result?.valid && <Link className="btn ghost" to={pick.startsWith('PLAN') ? '/student/plans' : '/student/orders'}>Go to checkout</Link>}
        </div>
        <p className="hint">Enter the code again at checkout to use it. Each coupon can be used once per account.</p>
      </form>

      <div className="card">
        <h2>Coupons you have used</h2>
        {mine.length === 0 ? <p className="muted">None yet.</p> : (
          <table>
            <thead><tr><th>Code</th><th>Discount</th><th>Used on</th></tr></thead>
            <tbody>
              {mine.map((c, i) => (
                <tr key={i}><td>{c.code}</td><td>{c.discountType === 'PERCENT' ? `${c.discountValue}%` : money(c.discountValue)}</td><td>{dateTime(c.usedAt)}</td></tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
