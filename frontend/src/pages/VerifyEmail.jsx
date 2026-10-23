import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { post } from '../api.js';
import AuthLayout, { Alert, errorText } from '../components/AuthLayout.jsx';

export default function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [state, setState] = useState({ status: 'working' });
  const ran = useRef(false); // the token is single use, so never submit it twice (StrictMode runs effects twice in dev)

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    if (!token) { setState({ status: 'error', error: { message: 'This link is missing its token.' } }); return; }
    post('/auth/verify-email', { token }, { auth: false })
      .then((r) => setState({ status: 'ok', message: r.message }))
      .catch((error) => setState({ status: 'error', error }));
  }, [token]);

  return (
    <AuthLayout title="Email verification">
      {state.status === 'working' && <p className="muted">Verifying…</p>}
      {state.status === 'ok' && <Alert kind="ok">{state.message}</Alert>}
      {state.status === 'error' && <Alert>{errorText(state.error)}</Alert>}
      <div className="links"><Link to="/login">Go to sign in</Link></div>
    </AuthLayout>
  );
}
