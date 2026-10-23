import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { post } from '../api.js';
import AuthLayout, { Alert, errorText } from '../components/AuthLayout.jsx';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [pw, setPw] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (pw !== confirm) { setError({ message: 'Passwords do not match' }); return; }
    setBusy(true); setError(null);
    try {
      setMsg((await post('/auth/reset-password', { token, newPassword: pw }, { auth: false })).message);
    } catch (err) { setError(err); } finally { setBusy(false); }
  }

  return (
    <AuthLayout title="Choose a new password">
      {msg ? (
        <>
          <Alert kind="ok">{msg}</Alert>
          <div className="links"><Link to="/login">Sign in</Link></div>
        </>
      ) : (
        <form onSubmit={submit}>
          <label htmlFor="pw">New password</label>
          <input id="pw" type="password" autoComplete="new-password" required minLength={10} value={pw} onChange={(e) => setPw(e.target.value)} />
          <label htmlFor="pw2">Confirm password</label>
          <input id="pw2" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          <p className="hint">At least 10 characters with upper case, lower case and a digit.</p>
          <Alert>{error && errorText(error)}</Alert>
          <button className="btn block" disabled={busy || !token}>Update password</button>
        </form>
      )}
    </AuthLayout>
  );
}
