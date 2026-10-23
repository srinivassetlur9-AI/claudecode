import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { dateOnly } from './format.js';
import PageTitle from './PageTitle.jsx';

export default function Subscription() {
  const [sub, setSub] = useState(null);
  const [ents, setEnts] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    api('/commerce/subscription').then(setSub).catch(setError);
    api('/me/entitlements').then(setEnts).catch(setError);
  }, []);

  return (
    <>
      <PageTitle name="My Subscription" />
      <Alert>{error && errorText(error)}</Alert>
      {sub && (
        <div className="card">
          <span className="muted">Current plan</span>
          <h2 style={{ fontSize: '1.5rem', margin: '.25rem 0' }}>{sub.planName} <span className="chip green">{sub.free ? 'Free' : 'Active'}</span></h2>
          {sub.free
            ? <p className="muted">You are on the free plan. Upgrade for the full course library, offline learning and more.</p>
            : <p className="muted">Started {dateOnly(sub.startsAt)} · {sub.endsAt ? `ends ${dateOnly(sub.endsAt)} (${sub.daysLeft} days left)` : 'no end date'}. Buy the plan again before it ends to extend it.</p>}
          <Link className="btn" to="/student/plans">{sub.free ? 'See plans' : 'Change or renew plan'}</Link>
        </div>
      )}

      <div className="card">
        <h2>What you can access</h2>
        {ents.length === 0 ? <p className="muted">Nothing purchased yet.</p> : (
          <table>
            <thead><tr><th>Type</th><th>Item</th><th>Valid until</th></tr></thead>
            <tbody>
              {ents.map((e, i) => (
                <tr key={i}>
                  <td>{e.type === 'FEATURE' ? 'Feature' : e.type === 'PLAN' ? 'Plan' : 'Course'}</td>
                  <td>{e.resourceRef === 'ALL_COURSES' ? 'Full course library' : e.resourceRef}</td>
                  <td>{e.validUntil ? dateOnly(e.validUntil) : 'Lifetime'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
