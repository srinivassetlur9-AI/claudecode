import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, Brand, errorText } from '../components/AuthLayout.jsx';
import Checkout from './Checkout.jsx';
import { dateOnly, money } from './format.js';
import PageTitle from './PageTitle.jsx';

const PER = { MONTHLY: '/ month', ANNUAL: '/ year', CORPORATE: '/ year' };

/** Plans & Pricing. standalone=true is the public /pricing page; otherwise it sits inside the app shell. */
export default function Plans({ standalone = false }) {
  const { user } = useAuth();
  const nav = useNavigate();
  const [plans, setPlans] = useState([]);
  const [current, setCurrent] = useState(null);
  const [selected, setSelected] = useState(null);
  const [done, setDone] = useState('');
  const [error, setError] = useState(null);

  const load = () => {
    api('/commerce/plans', { auth: false }).then(setPlans).catch(setError);
    if (user) api('/commerce/subscription').then(setCurrent).catch(() => {});
  };
  useEffect(load, [user]); // eslint-disable-line react-hooks/exhaustive-deps

  function choose(p) {
    if (!user) { nav('/login'); return; }
    setDone(''); setSelected({ type: 'PLAN', ref: p.code, title: `${p.name} plan`, price: p.pricePaise, currency: p.currency });
  }

  const body = (
    <>
      {standalone ? <h1>Choose a plan that fits your learning goals</h1> : <PageTitle name="Plans & Pricing" />}
      <p className="muted">{standalone ? '' : 'Choose a plan that fits your learning goals. '}Pay with UPI, cards, net banking or an international card.</p>
      {current && <p>Your current plan: <span className="chip green">{current.planName}</span>{current.endsAt && <span className="muted"> renews/ends {dateOnly(current.endsAt)}{current.daysLeft != null && ` (${current.daysLeft} days left)`}</span>}</p>}
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{done}</Alert>

      {selected && (
        <Checkout item={selected} onCancel={() => setSelected(null)}
                  onPaid={(o) => { setSelected(null); setDone(`Payment successful. ${o.title} is now active.`); load(); }} />
      )}

      <div className="plans">
        {plans.map((p) => {
          const isCurrent = current?.planCode === p.code;
          return (
            <div key={p.id} className={`card plan${p.planType === 'ANNUAL' ? ' popular' : ''}`}>
              {p.planType === 'ANNUAL' && <span className="chip">Most popular</span>}
              <h2>{p.name}</h2>
              <div className="price">{p.selfServe ? money(p.pricePaise, p.currency) : 'Custom'} <span className="muted">{PER[p.planType] || ''}</span></div>
              <p className="muted">{p.description}</p>
              <ul>{p.features.map((f) => <li key={f}>{f}</li>)}</ul>
              {p.planType === 'FREE' ? (
                <button className="btn ghost" disabled>{user ? (isCurrent ? 'Current plan' : 'Included') : 'Free for everyone'}</button>
              ) : !p.selfServe ? (
                <a className="btn ghost" href="mailto:sales@lms.local?subject=Enterprise%20plan">Contact sales</a>
              ) : (
                <button className="btn" onClick={() => choose(p)}>{isCurrent ? 'Renew / extend' : 'Subscribe'}</button>
              )}
            </div>
          );
        })}
      </div>
    </>
  );

  if (!standalone) return body;
  return (
    <div className="landing">
      <header className="landing-nav">
        <Brand />
        <nav>{user ? <Link className="btn" to="/dashboard">Dashboard</Link> : <><Link to="/login">Sign in</Link><Link className="btn" to="/register">Get started</Link></>}</nav>
      </header>
      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '1rem 1.5rem 3rem' }}>{body}</div>
    </div>
  );
}
