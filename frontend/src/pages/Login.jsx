import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { post } from '../api.js';
import AuthLayout, { Alert, errorText } from '../components/AuthLayout.jsx';

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState(null);
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/dashboard" replace />;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(null); setInfo('');
    try {
      await login(form.email, form.password);
      nav('/dashboard');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    try {
      const r = await post('/auth/resend-verification', { email: form.email, channel: 'EMAIL' }, { auth: false });
      setInfo(r.message); setError(null);
    } catch (err) { setError(err); }
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to continue learning.">
      <form onSubmit={submit}>
        <label htmlFor="email">Email</label>
        <input id="email" type="email" autoComplete="username" required value={form.email} onChange={set('email')} />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" autoComplete="current-password" required value={form.password} onChange={set('password')} />
        <Alert>{error && errorText(error)}</Alert>
        {error?.code === 'EMAIL_NOT_VERIFIED' && (
          <p className="hint"><a href="#resend" onClick={(e) => { e.preventDefault(); resend(); }}>Resend verification email</a></p>
        )}
        <Alert kind="ok">{info}</Alert>
        <button className="btn block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <div className="links">
        <Link to="/forgot-password">Forgot password?</Link>
        <Link to="/register">Create account</Link>
      </div>
    </AuthLayout>
  );
}
