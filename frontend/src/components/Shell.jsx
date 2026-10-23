import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { Brand } from './AuthLayout.jsx';
import StudentMenu from './StudentMenu.jsx';

export default function Shell({ children }) {
  const { user, logout, canAdminister } = useAuth();
  const nav = useNavigate();
  const initials = (user.firstName[0] + user.lastName[0]).toUpperCase();

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand"><Brand /></div>
        <NavLink to="/dashboard">Dashboard</NavLink>
        <NavLink to="/profile">My Profile</NavLink>
        <NavLink to="/security">Security &amp; Settings</NavLink>
        {canAdminister && <NavLink to="/users">Users</NavLink>}
        {user.roles.some((r) => ['SUPER_ADMIN', 'FINANCE_ADMIN', 'SUPPORT_AGENT'].includes(r)) && <NavLink to="/billing">Billing &amp; Devices</NavLink>}
        <div className="spacer" />
        <a href="#signout" onClick={async (e) => { e.preventDefault(); await logout(); nav('/login'); }}>Sign out</a>
      </aside>
      <div className="main">
        <header className="topbar">
          {user.roles.includes('LEARNER') && <StudentMenu />}
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontWeight: 600 }}>{user.firstName} {user.lastName}</div>
            <div className="muted" style={{ fontSize: '.8rem' }}>{user.roles.map(roleLabel).join(', ')}</div>
          </div>
          <div className="avatar">{initials}</div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}

export const roleLabel = (r) => r.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');
