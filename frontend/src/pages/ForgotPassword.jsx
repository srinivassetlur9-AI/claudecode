import { useState } from 'react';
import { Link } from 'react-router-dom';
import { post } from '../api.js';
import AuthLayout, { Alert, errorText } from '../components/AuthLayout.jsx';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      setMsg((await post('/auth/forgot-password', { email }, { auth: false })).message);
    } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Forgot password" subtitle="We'll email you a link to reset it.">
      <form onSubmit={submit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Alert>{error && errorText(error)}</Alert>
        <Alert kind="ok">{msg}</Alert>
        <button className="btn block" disabled={busy}>Send reset link</button>
      </form>
      <div className="links"><Link to="/login">Back to sign in</Link></div>
    </AuthLayout>
  );
}
