import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { del, post, put } from '../api.js';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, errorText } from '../components/AuthLayout.jsx';

const LANGUAGES = [['en', 'English'], ['hi', 'हिन्दी'], ['kn', 'ಕನ್ನಡ'], ['ta', 'தமிழ்'], ['te', 'తెలుగు'], ['es', 'Español'], ['fr', 'Français']];

export default function Security() {
  return (
    <>
      <h1>Security &amp; Settings</h1>
      <Language />
      <ChangePassword />
      <Sessions />
    </>
  );
}

function Language() {
  const { user, reload } = useAuth();
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);

  async function change(e) {
    setMsg(''); setError(null);
    try {
      await put('/me/settings', { language: e.target.value });
      await reload();
      setMsg('Language updated.');
    } catch (err) { setError(err); }
  }

  return (
    <div className="card">
      <h2>Language</h2>
      <select value={user.language} onChange={change} aria-label="Language preference" style={{ maxWidth: 260 }}>
        {LANGUAGES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{msg}</Alert>
    </div>
  );
}

function ChangePassword() {
  const { logout } = useAuth();
  const nav = useNavigate();
  const [f, setF] = useState({ currentPassword: '', newPassword: '' });
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    try {
      await post('/auth/change-password', f);
      // every session was revoked server side, including this one
      await logout();
      nav('/login');
    } catch (err) { setError(err); }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Change password</h2>
      <label htmlFor="cur">Current password</label>
      <input id="cur" type="password" autoComplete="current-password" required value={f.currentPassword} onChange={(e) => setF({ ...f, currentPassword: e.target.value })} />
      <label htmlFor="new">New password</label>
      <input id="new" type="password" autoComplete="new-password" required minLength={10} value={f.newPassword} onChange={(e) => setF({ ...f, newPassword: e.target.value })} />
      <p className="hint">You'll be signed out of every device afterwards.</p>
      <Alert>{error && errorText(error)}</Alert>
      <button className="btn" style={{ marginTop: '.75rem' }}>Change password</button>
    </form>
  );
}

function Sessions() {
  const { logout } = useAuth();
  const nav = useNavigate();
  const [rows, setRows] = useState([]);
  const [error, setError] = useState(null);

  const load = useCallback(() => api('/me/sessions').then(setRows).catch(setError), []);
  useEffect(() => { load(); }, [load]);

  async function revoke(id) {
    try { await del(`/me/sessions/${id}`); load(); } catch (err) { setError(err); }
  }

  async function revokeAll() {
    try { await del('/me/sessions'); await logout(); nav('/login'); } catch (err) { setError(err); }
  }

  return (
    <div className="card">
      <h2>Devices &amp; sessions</h2>
      <table>
        <thead><tr><th>Device</th><th>IP</th><th>Last active</th><th /></tr></thead>
        <tbody>
          {rows.map((s) => (
            <tr key={s.id}>
              <td>{(s.deviceName || 'Unknown device').slice(0, 60)} {s.current && <span className="chip green">This device</span>}</td>
              <td>{s.ipAddress}</td>
              <td>{new Date(s.lastSeenAt).toLocaleString()}</td>
              <td>{!s.current && <button className="btn danger small" onClick={() => revoke(s.id)}>Sign out</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Alert>{error && errorText(error)}</Alert>
      <button className="btn danger" style={{ marginTop: '1rem' }} onClick={revokeAll}>Sign out everywhere</button>
    </div>
  );
}
