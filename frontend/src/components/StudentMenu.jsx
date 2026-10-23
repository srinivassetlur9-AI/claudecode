import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

// Module 2 entry point. Each item is a student-facing page; admin tools live in the sidebar instead.
export const STUDENT_MENU = [
  ['/student/plans', 'Plans & Pricing', 'Free, monthly, annual, corporate and enterprise plans'],
  ['/student/subscription', 'My Subscription', 'Current plan, renewal and entitlements'],
  ['/student/orders', 'My Orders', 'Course purchases and payment confirmation'],
  ['/student/invoices', 'Invoices & Refunds', 'Download invoices and request refunds'],
  ['/student/coupons', 'Coupons', 'Apply and view discount codes'],
  ['/student/devices', 'My Devices', 'Your 2 registered devices and their history'],
];

export default function StudentMenu() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="dropdown" ref={ref}>
      <button type="button" className="btn ghost dropdown-toggle" aria-haspopup="menu" aria-expanded={open}
              onClick={() => setOpen((o) => !o)}>
        Student Subscription and Payment <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="dropdown-menu" role="menu">
          {STUDENT_MENU.map(([to, label, hint]) => (
            <Link key={to} to={to} role="menuitem" onClick={() => setOpen(false)}>
              <b>{label}</b>
              <span className="muted">{hint}</span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
