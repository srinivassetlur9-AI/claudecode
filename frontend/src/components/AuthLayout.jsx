import { Link } from 'react-router-dom';

export function Brand() {
  return (
    <Link to="/" className="brand" style={{ textDecoration: 'none' }}>
      <span className="mark">▲</span> IntelliLearn AI
    </Link>
  );
}

export default function AuthLayout({ title, subtitle, children }) {
  return (
    <div className="auth-wrap">
      <section className="auth-hero">
        <Brand />
        <h1>Learn Anywhere<br /><span>Grow Everywhere</span></h1>
        <p className="muted">AI-driven learning for a brighter tomorrow. World-class courses, expert instructors, certifications and much more.</p>
        <div className="stats">
          <div><b>500+</b><span className="muted">Courses</span></div>
          <div><b>100K+</b><span className="muted">Learners</span></div>
          <div><b>500+</b><span className="muted">Instructors</span></div>
        </div>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <h1>{title}</h1>
          {subtitle && <p className="muted" style={{ marginTop: 0 }}>{subtitle}</p>}
          {children}
        </div>
      </section>
    </div>
  );
}

export function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function errorText(e) {
  if (!e) return '';
  const extra = e.details?.length ? ` (${e.details.join('; ')})` : '';
  return (e.message || 'Something went wrong') + extra;
}
