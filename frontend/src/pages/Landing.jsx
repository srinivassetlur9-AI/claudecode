import { Link } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { Brand } from '../components/AuthLayout.jsx';

const FEATURES = [
  ['AI-driven learning', 'Personalised paths that adapt to how you learn.'],
  ['Expert instructors', 'Learn from practitioners who teach what they do.'],
  ['Certifications', 'Earn recognised certificates as you complete courses.'],
];

export default function Landing() {
  const { user, loading } = useAuth();

  return (
    <div className="landing">
      <header className="landing-nav">
        <Brand />
        <nav>
          <Link to="/pricing">Pricing</Link>
          {loading ? null : user ? (
            <Link className="btn" to="/dashboard">Go to dashboard</Link>
          ) : (
            <>
              <Link to="/login">Sign in</Link>
              <Link className="btn" to="/register">Get started</Link>
            </>
          )}
        </nav>
      </header>

      <section className="landing-hero">
        <h1>Learn Anywhere<br /><span>Grow Everywhere</span></h1>
        <p className="muted">AI-driven learning for a brighter tomorrow. World-class courses, expert instructors, certifications and much more.</p>
        {!user && !loading && (
          <div className="landing-cta">
            <Link className="btn" to="/register">Create your account</Link>
            <Link className="btn ghost" to="/login">Sign in</Link>
          </div>
        )}
        <div className="stats">
          <div><b>500+</b><span className="muted">Courses</span></div>
          <div><b>100K+</b><span className="muted">Learners</span></div>
          <div><b>500+</b><span className="muted">Instructors</span></div>
        </div>
      </section>

      <section className="landing-features">
        {FEATURES.map(([t, d]) => (
          <div className="card" key={t}><h3>{t}</h3><p className="muted">{d}</p></div>
        ))}
      </section>
    </div>
  );
}
