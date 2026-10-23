# Module 1 - Identity & User Management

## Run it
```
cp .env.example .env        # set JWT_SECRET etc.; SEED_DEMO_USERS=true for a local try-out
docker compose up --build   # UI http://localhost:8081, API http://localhost:8080
```
Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `MAIL_FROM` to send real emails. With `MAIL_HOST` empty (non-prod only), links/OTPs are written to the backend log (`[DEV MAIL]`, `[DEV SMS]`).
Demo users (only with `SEED_DEMO_USERS=true`): `<role>@lms.local`, e.g. `learner@lms.local`, `org.admin@lms.local`,
`super.admin@lms.local`, password `Demo@12345678`.

Without Docker: `cd backend && mvn spring-boot:run` (needs MySQL) and `cd frontend && npm install && npm run dev`.
Tests: `cd backend && mvn test` (H2 in MySQL mode; covers the acceptance criteria below).

## 1. Frontend screens (IntelliLearn AI look and feel)
Login, Register (learner / instructor / organization, optional mobile OTP step), Verify email, Forgot password,
Reset password, Dashboard, My Profile, Security & Settings (language, change password, devices & sessions),
Users (admin: search, invite, assign roles, enable/disable).

## 2. Architecture
```
React SPA --/api/v1/identity--> Spring Boot (Identity service) --> MySQL
                                   ^  token validation, can(), entitlements, sessions
Module 2 / Module 3 services ------+
```
One auth service issues all tokens. Other modules never see passwords; they validate the bearer token and ask `can()`.

Backend packages: `security` (JWT, filters, permission matrix), `service` (auth, sessions, tokens, admin, profile),
`web` (controllers, DTOs, error handler), `domain` / `repo` (JPA), `config`.

## 3. Database (Flyway `V1__identity.sql`)
`organizations`, `users`, `user_profiles`, `user_roles`, `verification_tokens`, `user_sessions`, `user_entitlements`
(unused hook for Module 2). All carry `id, created_at, updated_at, created_by`; `users` and `organizations` soft-delete.
Verification tokens and refresh tokens are stored as SHA-256 hashes only.

## 4. API (`/api/v1/identity`)
| Method | Path | Who |
|---|---|---|
| POST | /auth/register, /auth/login, /auth/refresh, /auth/logout | public |
| POST | /auth/verify-email, /auth/verify-mobile, /auth/resend-verification | public |
| POST | /auth/forgot-password, /auth/reset-password | public |
| POST | /auth/change-password | signed in |
| GET/PUT | /me, PUT /me/settings | signed in |
| GET/DELETE | /me/sessions, /me/sessions/{id}; GET /me/entitlements | signed in |
| GET/POST | /users, GET /users/{id} | scoped by role (SUPER_ADMIN all, ORG_ADMIN own org, SUPPORT_AGENT read) |
| PUT | /users/{id}/roles, /users/{id}/status | SUPER_ADMIN, ORG_ADMIN (own org, limited roles) |
| GET | /users/{id}/sessions, /users/{id}/entitlements | self, or admin with access |
| GET | /auth/validate | contract: token -> user id, organization id, roles |
| POST | /authz/can | contract: `{action, resourceType, resourceId, organizationId, ownerId}` -> `{allowed}` |

Errors: `{timestamp, status, code, message, details[], path}`.

## 5. UI architecture
Vite + React 18 + React Router. `api.js` keeps the access token in memory, the refresh token in `localStorage`, and
does one shared refresh-and-retry on 401. `auth.jsx` holds the user and restores the session on reload.
`Protected` route wrapper + role-aware menu (server remains the enforcer).

## Contracts for later modules
- **Token validation**: call `GET /auth/validate` with the user's bearer token, or verify the HS256 JWT locally
  (claims `sub`, `org`, `roles`, `sid`; share `JWT_SECRET`) and check the session is live.
- **Permissions**: `can(user, action, resource)` is `AuthorizationService`; scopes are ALL / ORG / OWN. Add new
  actions to its matrix (e.g. `course:manage`).
- **Entitlements**: implement `EntitlementProvider` (default reads the empty `user_entitlements` table).
- **Devices**: `GET /users/{id}/sessions` lists active sessions, one per login, for device-limit enforcement.

## Security notes
BCrypt(12); 15-minute access tokens; rotating refresh tokens with reuse detection (replay revokes all sessions);
sessions revoked on logout, password change/reset, disable and role change; lockout after 5 failures for 15 min;
per-IP rate limit on auth endpoints (in memory, per instance); single-use expiring tokens; OTP max 5 attempts;
generic responses for forgot-password/resend (no account enumeration; register does return 409 for a taken email).

## Not done / follow-ups
- SMS delivery (email goes out over SMTP when `MAIL_HOST` is set; mobile OTPs still only log, and only in non-prod with `LOG_OTP=true` or the dev sender).
- OAuth / social login and MFA (JWT + refresh is implemented; OAuth providers are not).
- Shared rate limiter (Redis/gateway) for multi-instance deployment; HTTPS termination; secret management.
- Refresh token in an HttpOnly cookie instead of `localStorage`.
- Frontend tests; audit log of auth events.
