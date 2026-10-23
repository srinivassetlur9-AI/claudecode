import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, post } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { STATUS_CHIP, dateTime } from './format.js';
import PageTitle from './PageTitle.jsx';

export default function Devices() {
  const { logout } = useAuth();
  const nav = useNavigate();
  const [data, setData] = useState(null);
  const [history, setHistory] = useState([]);
  const [msg, setMsg] = useState('');
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    api('/commerce/devices').then(setData).catch(setError);
    api('/commerce/devices/history').then(setHistory).catch(setError);
  }, []);
  useEffect(load, [load]);

  async function release(d, action) {
    const verb = action === 'replace' ? 'replace' : 'deactivate';
    const warn = d.current ? ' This is the device you are using now, so you will be signed out.' : '';
    if (!window.confirm(`${verb[0].toUpperCase()}${verb.slice(1)} "${d.name}"?${warn}`)) return;
    setError(null); setMsg('');
    try {
      await post(`/commerce/devices/${d.id}/${action}`, {});
      if (d.current) { await logout(); nav('/login'); return; }
      setMsg(action === 'replace' ? `"${d.name}" was replaced. Sign in on your new device to use the free slot.` : `"${d.name}" was deactivated.`);
      load();
    } catch (err) { setError(err); }
  }

  if (!data) return <><PageTitle name="My Devices" /><Alert>{error && errorText(error)}</Alert></>;

  const active = data.devices.filter((d) => d.status === 'ACTIVE');
  const past = data.devices.filter((d) => d.status !== 'ACTIVE');
  const slots = Array.from({ length: data.maxDevices }, (_, i) => active[i] || null);

  return (
    <>
      <PageTitle name="My Devices" />
      <p className="muted">Your account works on at most {data.maxDevices} registered devices. A third device is blocked until you free a slot.</p>
      <Alert>{error && errorText(error)}</Alert>
      <Alert kind="ok">{msg}</Alert>

      <div className="slots">
        {slots.map((d, i) => (
          <div key={i} className={`card slot${d ? '' : ' empty'}`}>
            <span className="muted">Device {i + 1} of {data.maxDevices}</span>
            {d ? (
              <>
                <h2 style={{ margin: '.25rem 0' }}>{d.name} {d.current && <span className="chip green">This device</span>}</h2>
                <p className="muted" style={{ margin: 0 }}>Registered {dateTime(d.registeredAt)}<br />Last active {dateTime(d.lastSeenAt)}{d.lastIp && ` · ${d.lastIp}`}</p>
                <div className="toolbar" style={{ marginTop: '.75rem', marginBottom: 0 }}>
                  <button className="btn ghost small" onClick={() => release(d, 'replace')}>Replace</button>
                  <button className="btn danger small" onClick={() => release(d, 'deactivate')}>Deactivate</button>
                </div>
              </>
            ) : <h2 style={{ margin: '.25rem 0' }}>Free slot</h2>}
          </div>
        ))}
      </div>

      {past.length > 0 && (
        <div className="card">
          <h2>Previous devices</h2>
          <table>
            <thead><tr><th>Device</th><th>Status</th><th>Since</th><th>Reason</th></tr></thead>
            <tbody>
              {past.map((d) => (
                <tr key={d.id}><td>{d.name}</td><td><span className={`chip ${STATUS_CHIP[d.status] || ''}`}>{d.status}</span></td><td>{dateTime(d.deactivatedAt)}</td><td>{d.deactivationReason || '—'}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="card">
        <h2>Device history</h2>
        {history.length === 0 ? <p className="muted">No activity yet.</p> : (
          <table>
            <thead><tr><th>When</th><th>Event</th><th>Detail</th><th>IP</th></tr></thead>
            <tbody>
              {history.map((e) => (
                <tr key={e.id}>
                  <td>{dateTime(e.createdAt)}</td>
                  <td><span className={`chip ${e.eventType === 'BLOCKED' ? 'red' : e.eventType === 'LOGIN' ? '' : 'amber'}`}>{e.eventType}</span></td>
                  <td>{e.detail || '—'}</td><td>{e.ipAddress || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
