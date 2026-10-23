import { useState } from 'react';
import { Link } from 'react-router-dom';
import { post } from '../api.js';
import AuthLayout, { Alert, errorText } from '../components/AuthLayout.jsx';

const TYPES = [
  ['LEARNER', 'Learner'],
  ['INSTRUCTOR', 'Instructor'],
  ['ORGANIZATION', 'Organization'],
];

export default function Register() {
  const [form, setForm] = useState({ accountType: 'LEARNER', firstName: '', lastName: '', email: '', mobile: '', password: '', organizationName: '' });
  const [error, setError] = useState(null);
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const body = { ...form, mobile: form.mobile || null, organizationName: form.accountType === 'ORGANIZATION' ? form.organizationName : null };
      const r = await post('/auth/register', body, { auth: false });
      setDone(r.message);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <AuthLayout title="Check your email">
        <Alert kind="ok">{done}</Alert>
        <p className="muted">Open the link we sent to <b>{form.email}</b> to activate your account.</p>
        {form.mobile && <VerifyMobile email={form.email} />}
        <div className="links"><Link to="/login">Back to sign in</Link></div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start learning or teaching in minutes.">
      <form onSubmit={submit}>
        <label htmlFor="type">I am a</label>
        <select id="type" value={form.accountType} onChange={set('accountType')}>
          {TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        {form.accountType === 'ORGANIZATION' && (
          <>
            <label htmlFor="org">Organization name</label>
            <input id="org" required maxLength={200} value={form.organizationName} onChange={set('organizationName')} />
          </>
        )}
        <div className="row">
          <div><label htmlFor="fn">First name</label><input id="fn" required maxLength={100} value={form.firstName} onChange={set('firstName')} /></div>
          <div><label htmlFor="ln">Last name</label><input id="ln" required maxLength={100} value={form.lastName} onChange={set('lastName')} /></div>
        </div>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="username" required value={form.email} onChange={set('email')} />
        <label htmlFor="mobile">Mobile (optional)</label>
        <input id="mobile" type="tel" placeholder="+919876543210" value={form.mobile} onChange={set('mobile')} />
        <label htmlFor="pw">Password</label>
        <input id="pw" type="password" autoComplete="new-password" required minLength={10} value={form.password} onChange={set('password')} />
        <p className="hint">At least 10 characters with upper case, lower case and a digit.</p>
        <Alert>{error && errorText(error)}</Alert>
        <button className="btn block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
      </form>
      <div className="links"><span className="muted">Already registered?</span><Link to="/login">Sign in</Link></div>
    </AuthLayout>
  );
}

function VerifyMobile({ email }) {
  const [otp, setOtp] = useState('');
  const [msg, setMsg] = useState(null);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    try {
      setMsg((await post('/auth/verify-mobile', { email, otp }, { auth: false })).message);
    } catch (err) { setError(err); }
  }

  return (
    <form onSubmit={submit}>
      <label htmlFor="otp">Mobile verification code</label>
      <input id="otp" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required value={otp} onChange={(e) => setOtp(e.target.value)} />
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{msg}</Alert>
      <button className="btn ghost block">Verify mobile</button>
    </form>
  );
}
