// Thin fetch wrapper for the Identity API. The access token lives in memory only; the refresh token is kept in
// localStorage so a reload can restore the session. A 401 triggers one refresh-and-retry.
const BASE = '/api/v1/identity';
const REFRESH_KEY = 'lms.refresh';

let accessToken = null;
let onSessionLost = () => {};
let refreshing = null;

export const setSessionLostHandler = (fn) => { onSessionLost = fn; };
export const hasStoredSession = () => !!localStorage.getItem(REFRESH_KEY);

export function storeTokens(t) {
  accessToken = t.accessToken;
  localStorage.setItem(REFRESH_KEY, t.refreshToken);
}

export function clearTokens() {
  accessToken = null;
  localStorage.removeItem(REFRESH_KEY);
}

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || 'Request failed');
    this.status = status;
    this.code = body?.code;
    this.details = body?.details || [];
  }
}

// Module 2 (commerce) lives next to identity under /api/v1; paths starting with /commerce go there.
const urlFor = (path) => (path.startsWith('/commerce') ? '/api/v1' : BASE) + path;

// Learners may use at most two registered devices, so every request says which install it comes from.
// The id is random, created once per browser and never leaves it except in this header.
const DEVICE_KEY = 'lms.deviceId';
let memoryDeviceId = null;
export function deviceId() {
  try {
    let id = localStorage.getItem(DEVICE_KEY);
    if (!id) {
      id = crypto.randomUUID ? crypto.randomUUID() : `d-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      localStorage.setItem(DEVICE_KEY, id);
    }
    return id;
  } catch {
    memoryDeviceId ??= `d-${Date.now()}-${Math.random().toString(36).slice(2)}`; // storage blocked: stable for this page load only
    return memoryDeviceId;
  }
}

function deviceName() {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'unknown OS';
  return `${browser} on ${os}`;
}

async function raw(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json', 'X-Device-Id': deviceId(), 'X-Device-Name': deviceName() };
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const res = await fetch(urlFor(path), { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

export async function refreshSession() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) throw new ApiError(401, { message: 'No session' });
  // Share one in-flight refresh: the refresh token rotates, so parallel refreshes would trip reuse detection.
  refreshing ??= raw('/auth/refresh', { method: 'POST', body: { refreshToken }, auth: false })
    .then((t) => { storeTokens(t); return t; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

export async function api(path, opts = {}) {
  try {
    return await raw(path, opts);
  } catch (e) {
    if (e.status === 401 && opts.auth !== false && hasStoredSession()) {
      try {
        await refreshSession();
      } catch {
        clearTokens();
        onSessionLost();
        throw e;
      }
      return raw(path, opts);
    }
    throw e;
  }
}

export const post = (path, body, opts) => api(path, { method: 'POST', body, ...opts });
export const put = (path, body) => api(path, { method: 'PUT', body });
export const del = (path) => api(path, { method: 'DELETE' });
