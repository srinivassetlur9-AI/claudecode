import { useEffect, useState } from 'react';
import { api, put } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, errorText } from '../components/AuthLayout.jsx';

export default function Profile() {
  const { reload } = useAuth();
  const [form, setForm] = useState(null);
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);

  useEffect(() => {
    api('/me').then((p) => setForm({
      firstName: p.user.firstName, lastName: p.user.lastName, mobile: p.user.mobile || '',
      headline: p.headline || '', bio: p.bio || '', avatarUrl: p.avatarUrl || '', country: p.country || '', timezone: p.timezone || '',
    })).catch(setError);
  }, []);

  if (!form) return <p className="muted">{error ? errorText(error) : 'Loading…'}</p>;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function save(e) {
    e.preventDefault();
    setMsg(''); setError(null);
    try {
      await put('/me', { ...form, mobile: form.mobile || null });
      await reload();
      setMsg('Profile saved.');
    } catch (err) { setError(err); }
  }

  return (
    <>
      <h1>My Profile</h1>
      <form className="card" onSubmit={save}>
        <div className="row">
          <div><label htmlFor="fn">First name</label><input id="fn" required value={form.firstName} onChange={set('firstName')} /></div>
          <div><label htmlFor="ln">Last name</label><input id="ln" required value={form.lastName} onChange={set('lastName')} /></div>
        </div>
        <label htmlFor="mobile">Mobile</label>
        <input id="mobile" type="tel" value={form.mobile} onChange={set('mobile')} />
        <p className="hint">Changing your number means it has to be verified again.</p>
        <label htmlFor="headline">Headline</label>
        <input id="headline" maxLength={200} value={form.headline} onChange={set('headline')} />
        <label htmlFor="bio">About</label>
        <textarea id="bio" maxLength={2000} value={form.bio} onChange={set('bio')} />
        <label htmlFor="avatar">Avatar URL (https)</label>
        <input id="avatar" value={form.avatarUrl} onChange={set('avatarUrl')} />
        <div className="row">
          <div><label htmlFor="country">Country code</label><input id="country" maxLength={2} placeholder="IN" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value.toUpperCase() })} /></div>
          <div><label htmlFor="tz">Timezone</label><input id="tz" placeholder="Asia/Kolkata" value={form.timezone} onChange={set('timezone')} /></div>
        </div>
        <Alert>{error && errorText(error)}</Alert>
        <Alert kind="ok">{msg}</Alert>
        <button className="btn" style={{ marginTop: '1rem' }}>Save changes</button>
      </form>
    </>
  );
}
