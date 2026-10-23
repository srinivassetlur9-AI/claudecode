import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, clearTokens, hasStoredSession, post, refreshSession, setSessionLostHandler, storeTokens } from './api.js';

const AuthContext = createContext(null);
export const useAuth = () => useContext(AuthContext);

// Mirrors the backend permission matrix closely enough to decide which menu items to show.
// The server is still the only thing that actually enforces access.
const ADMIN_ROLES = ['SUPER_ADMIN', 'ORG_ADMIN', 'SUPPORT_AGENT'];

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(hasStoredSession());

  useEffect(() => {
    setSessionLostHandler(() => setUser(null));
    if (!hasStoredSession()) return;
    refreshSession()
      .then((t) => setUser(t.user))
      .catch(() => clearTokens())
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email, password) => {
    const t = await post('/auth/login', { email, password, deviceName: navigator.userAgent.slice(0, 100) }, { auth: false });
    storeTokens(t);
    setUser(t.user);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = localStorage.getItem('lms.refresh');
    try { await post('/auth/logout', { refreshToken }, { auth: false }); } catch { /* already gone */ }
    clearTokens();
    setUser(null);
  }, []);

  const reload = useCallback(async () => {
    const me = await api('/me');
    setUser(me.user);
    return me;
  }, []);

  const value = useMemo(() => ({
    user, loading, login, logout, reload,
    canAdminister: !!user && user.roles.some((r) => ADMIN_ROLES.includes(r)),
  }), [user, loading, login, logout, reload]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
