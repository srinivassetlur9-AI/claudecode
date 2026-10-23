import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Shell from './components/Shell.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import VerifyEmail from './pages/VerifyEmail.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Landing from "./pages/Landing.jsx";
import Dashboard from './pages/Dashboard.jsx';
import Profile from './pages/Profile.jsx';
import Security from './pages/Security.jsx';
import Plans from "./student/Plans.jsx";
import Subscription from "./student/Subscription.jsx";
import Orders from "./student/Orders.jsx";
import Invoices from "./student/Invoices.jsx";
import Coupons from "./student/Coupons.jsx";
import Devices from "./student/Devices.jsx";
import CommerceAdmin from "./student/CommerceAdmin.jsx";
import Users from './pages/Users.jsx';

const BILLING_ROLES = ['SUPER_ADMIN', 'FINANCE_ADMIN', 'SUPPORT_AGENT'];

function Protected({ children, adminOnly, billingOnly }) {
  const { user, loading, canAdminister } = useAuth();
  if (loading) return <div className="center muted">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && !canAdminister) return <Navigate to="/dashboard" replace />;
  if (billingOnly && !user.roles.some((r) => BILLING_ROLES.includes(r))) return <Navigate to="/dashboard" replace />;
  return <Shell>{children}</Shell>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/" element={<Landing />} />
      <Route path="/dashboard" element={<Protected><Dashboard /></Protected>} />
      <Route path="/profile" element={<Protected><Profile /></Protected>} />
      <Route path="/security" element={<Protected><Security /></Protected>} />
      <Route path="/users" element={<Protected adminOnly><Users /></Protected>} />
      <Route path="/pricing" element={<Plans standalone />} />
      <Route path="/student/plans" element={<Protected><Plans /></Protected>} />
      <Route path="/student/subscription" element={<Protected><Subscription /></Protected>} />
      <Route path="/student/orders" element={<Protected><Orders /></Protected>} />
      <Route path="/student/invoices" element={<Protected><Invoices /></Protected>} />
      <Route path="/student/coupons" element={<Protected><Coupons /></Protected>} />
      <Route path="/student/devices" element={<Protected><Devices /></Protected>} />
      <Route path="/billing" element={<Protected billingOnly><CommerceAdmin /></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
