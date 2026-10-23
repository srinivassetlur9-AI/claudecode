import { useCallback, useEffect, useState } from 'react';
import { api, post, put } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Alert, errorText } from '../components/AuthLayout.jsx';
import { roleLabel } from '../components/Shell.jsx';

const ALL_ROLES = ['LEARNER', 'INSTRUCTOR', 'ORG_ADMIN', 'SUPER_ADMIN', 'CONTENT_ADMIN', 'EXAM_ADMIN', 'EVENT_ADMIN', 'FINANCE_ADMIN', 'EVALUATOR', 'SUPPORT_AGENT'];
const ORG_ROLES = ['LEARNER', 'INSTRUCTOR', 'ORG_ADMIN'];

export default function Users() {
  const { user: me } = useAuth();
  const isSuper = me.roles.includes('SUPER_ADMIN');
  const canManage = isSuper || me.roles.includes('ORG_ADMIN'); // support agents are read-only
  const assignable = isSuper ? ALL_ROLES : ORG_ROLES;

  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ items: [], total: 0, size: 20 });
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [inviting, setInviting] = useState(false);

  const load = useCallback(() => {
    api(`/users?page=${page}&size=20&q=${encodeURIComponent(q)}`).then(setData).catch(setError);
  }, [page, q]);
  useEffect(() => { load(); }, [load]);

  async function toggleStatus(u) {
    setError(null);
    try {
      await put(`/users/${u.id}/status`, { status: u.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED' });
      load();
    } catch (err) { setError(err); }
  }

  const pages = Math.max(1, Math.ceil(data.total / data.size));

  return (
    <>
      <h1>Users</h1>
      <div className="toolbar">
        <input placeholder="Search name or email" value={q} onChange={(e) => { setPage(0); setQ(e.target.value); }} />
        {canManage && <button className="btn" onClick={() => setInviting(true)}>Invite user</button>}
      </div>
      <Alert>{error && errorText(error)}</Alert>
      <div className="card" style={{ overflowX: 'auto' }}>
        <table>
          <thead><tr><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th /></tr></thead>
          <tbody>
            {data.items.map((u) => (
              <tr key={u.id}>
                <td>{u.firstName} {u.lastName}</td>
                <td>{u.email}</td>
                <td>{u.roles.map((r) => <span key={r} className="chip">{roleLabel(r)}</span>)}</td>
                <td><span className={`chip ${u.status === 'ACTIVE' ? 'green' : u.status === 'PENDING' ? 'amber' : 'red'}`}>{u.status}</span></td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  {canManage && u.id !== me.id && (
                    <>
                      <button className="btn ghost small" onClick={() => setEditing(u)}>Roles</button>{' '}
                      <button className="btn danger small" onClick={() => toggleStatus(u)}>{u.status === 'DISABLED' ? 'Enable' : 'Disable'}</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="toolbar" style={{ marginTop: '1rem', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="muted">{data.total} users</span>
          <span>
            <button className="btn ghost small" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
            <span className="muted">Page {page + 1} of {pages}</span>{' '}
            <button className="btn ghost small" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button>
          </span>
        </div>
      </div>

      {editing && <RoleDialog user={editing} roles={assignable} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
      {inviting && <InviteDialog roles={assignable} onClose={() => setInviting(false)} onSaved={() => { setInviting(false); load(); }} />}
    </>
  );
}

function RolePicker({ roles, value, onChange }) {
  const toggle = (r) => onChange(value.includes(r) ? value.filter((x) => x !== r) : [...value, r]);
  return (
    <div className="checks">
      {roles.map((r) => (
        <label key={r}><input type="checkbox" checked={value.includes(r)} onChange={() => toggle(r)} /> {roleLabel(r)}</label>
      ))}
    </div>
  );
}

function RoleDialog({ user, roles, onClose, onSaved }) {
  const [value, setValue] = useState(user.roles);
  const [error, setError] = useState(null);

  async function save() {
    try { await put(`/users/${user.id}/roles`, { roles: value }); onSaved(); } catch (err) { setError(err); }
  }

  return (
    <div className="modal-bg" role="dialog" aria-modal="true" aria-label="Edit roles">
      <div className="modal">
        <h2>Roles for {user.firstName} {user.lastName}</h2>
        <RolePicker roles={roles} value={value} onChange={setValue} />
        <p className="hint">They will be signed out so the new roles apply immediately.</p>
        <Alert>{error && errorText(error)}</Alert>
        <div className="toolbar" style={{ marginTop: '1rem' }}>
          <button className="btn" disabled={value.length === 0} onClick={save}>Save</button>
          <button className="btn ghost" onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  );
}

function InviteDialog({ roles, onClose, onSaved }) {
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', roles: ['LEARNER'] });
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    try { await post('/users', f); onSaved(); } catch (err) { setError(err); }
  }

  return (
    <div className="modal-bg" role="dialog" aria-modal="true" aria-label="Invite user">
      <form className="modal" onSubmit={submit}>
        <h2>Invite user</h2>
        <div className="row">
          <div><label htmlFor="ifn">First name</label><input id="ifn" required value={f.firstName} onChange={(e) => setF({ ...f, firstName: e.target.value })} /></div>
          <div><label htmlFor="iln">Last name</label><input id="iln" required value={f.lastName} onChange={(e) => setF({ ...f, lastName: e.target.value })} /></div>
        </div>
        <label htmlFor="iem">Email</label>
        <input id="iem" type="email" required value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
        <label>Roles</label>
        <RolePicker roles={roles} value={f.roles} onChange={(r) => setF({ ...f, roles: r })} />
        <p className="hint">They'll receive a link to set their own password.</p>
        <Alert>{error && errorText(error)}</Alert>
        <div className="toolbar" style={{ marginTop: '1rem' }}>
          <button className="btn" disabled={f.roles.length === 0}>Send invite</button>
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
        </div>
      </form>
    </div>
  );
}
