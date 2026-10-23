---
name: learning-management-system
description: Build and extend Project LMS, an integrated enterprise Learning Management System delivered module by module. Use for any LMS work - identity and user management (registration, login, JWT/OAuth, RBAC, profiles), subscriptions and commerce, device management, course management, video and offline learning - including screens, architecture, database schema, APIs and code.
---

# Learning Management System (LMS)

Version 1.0.0 · Author: Srinivas R Setlur

## Purpose
Project LMS is one integrated enterprise LMS. Every module integrates through common APIs,
shared data contracts, a single authentication service, entitlements and end-to-end workflows.
The LMS is built incrementally: one module at a time, each one building on the contracts of
the modules before it.

## Module index

| # | Module | Status | Build it now? |
|---|--------|--------|---------------|
| 1 | Identity & User Management | In development | Yes |
| 2 | Student Subscription and Payment | In development | Yes |
| 3 | Course Management, Video & Offline Learning | Planned | No - integration hooks only |

Rules for planned modules:
- Do not implement a module whose status is Planned.
- Do leave clean integration points for it (see "Contracts Module 1 must expose").
- When a module is started, its section below is filled in and its status changes here.

## Shared conventions (all modules)
- Tech stack: React JS (frontend), Java Spring Boot (backend), MySQL (database), Docker (deployment).
- One auth service: every module validates tokens issued by Module 1; no module stores passwords.
- API style: REST, versioned under /api/v1/<module>/..., JSON, consistent error format.
- Every table carries id, created_at, updated_at, created_by; soft delete where records are audited.
- UI: follow the look and feel in "assets/LMS Look & Feel.png" (kept inside this skill folder).

## Workflow
1. Identify which module the request belongs to, using the module index.
2. If the module is Planned, say so and offer to add only the integration hooks.
3. Read the existing code and schema first; extend it rather than regenerating it.
4. Deliver the outputs listed under Output format for that module only.
5. Check the module's acceptance criteria before calling it done.

## Module 1 - Identity & User Management
### Scope
1. User registration for learners, instructors and organizations.
2. Email/mobile verification, account activation, forgot password and password reset.
3. Login, logout, JWT/OAuth authentication, refresh tokens and session management.
4. Role-Based Access Control for: Learner, Instructor, Organization Admin, Super Admin,
   Content Admin, Exam Admin, Event Admin, Finance Admin, Evaluator, Support Agent.
5. User profile, account settings, language preference and security settings.
6. Authentication and authorization APIs used by all other modules.
7. Screens follow the design in "assets/LMS Look & Feel.png".
   - Page background: a very pale blue-white wash (only a hint of blue; the blue must not be strong), not flat grey.
     A teacher-and-student illustration sits behind the content, bottom-right, on the app pages, the landing page and the auth pages (login, register, forgot/reset password); on the auth pages it is centred in
     the right-hand panel, directly under the form card, rather than pushed to the far right edge
     (frontend/src/assets/teacher-student.svg). It is soft in colour so text remains easy to read.
   - The illustration must be visible, not hidden behind solid white blocks: cards, the left menu and the top bar are
     semi-transparent (about 45-75% white) so the picture shows through, and there are no large empty plain areas that
     cover it. Modals and form inputs stay solid white. Text must stay easy to read.
   - Zoom: the whole app renders at 80% zoom so more fits on screen and the background picture is visible. Done by setting
     the root font size to 80% (--ui-scale in frontend/src/styles.css) with all sizes in rem, not px (breakpoints and
     1px borders excepted). To change the zoom, change only --ui-scale.
   - Font: Inter (self-hosted through the @fontsource/inter package, no external font request), weights 400-700.
   - Buttons: small and calm, not bright. Soft blue (#4a6cc4, hover #3c5aa8), compact padding (about .45rem x .9rem),
     font size .88rem, weight 500, 8px radius. The same soft blue is used for links and the active menu item.
   - Plans & Pricing: all plans (Free, Monthly, Annual, Corporate, Enterprise) sit side by side in one row on normal
     desktop widths; they only wrap onto further rows on narrow screens (below about 1100px).
   - Left menu: compact. Small padding and gaps between items, a narrow column (about 208px), and "Sign out" directly
     under the last menu item, with no large blank space between items or above the Sign out link.
8. Built with React JS, Java Spring Boot, MySQL and Docker.

### Contracts Module 1 must expose (used by later modules)
- Token validation endpoint / middleware that returns user id, organization id and roles.
- Permission check: can(user, action, resource).
- Entitlements interface: an empty, documented hook that Module 2 will populate
  (e.g. which courses or plans a user has access to).
- Device/session list per user, so Module 2 can enforce device limits.

### Security requirements
- Passwords hashed with Argon2 or bcrypt; never logged or returned.
- Short-lived access tokens, rotating refresh tokens, revocation on logout and password change.
- Rate limiting and lockout on login, OTP and password-reset endpoints.
- Verification and reset links/OTPs are single use and expire.
- Follow OWASP ASVS for authentication and session management.

### Acceptance criteria
- Each of the 10 roles can log in and sees only what its permissions allow.
- Registration -> verification -> activation -> login -> reset password works end to end.
- Another module can protect an endpoint using only the exposed contracts.

## Module 2 - Student Subscription and Payment 
1.Free, monthly, annual, corporate and enterprise subscription plans.
2.Course purchase, orders, payment confirmation, invoices, refunds and coupons.
3.Payment gateway integration architecture for UPI, cards, net banking and international methods.
4.For Payment gateway use the Razor pay 
4.Entitlement service controlling access to courses, subscriptions and purchased content.
5.Mandatory maximum of two dedicated registered devices per learner account.
6.Device registration, activation, deactivation, replacement, history and audit.
7.Third-device blocking and device/license validation APIs.

### Module 2 UI rules
- Module 2 is reached from a dropdown in the header named "Student Subscription and Payment". The dropdown is left-aligned
  (button at the left of the header, menu opening from its left edge, left-aligned text).
- The dropdown links are: Plans & Pricing, My Subscription, My Orders, Invoices & Refunds, Coupons, My Devices.
- Clicking a link opens that page as a new page (its own route, not a modal and not a new browser tab).
- Every such page shows the heading "Student Subscription and Payment" followed, next to it on the same line, by the
  name of the link that was clicked, for example: Student Subscription and Payment › My Devices.
- Admin and finance screens for this module are not in the dropdown; they live in the left sidebar under "Billing & Devices".

## Module 3 - Course Management, Video & Offline Learning
Status: Planned - to be added later.

## Output format (per module)
- Frontend screens
- Module architecture and how it fits the end-to-end system architecture
- Database schema (new and changed tables)
- API endpoints
- UI architecture
- Production-ready code
- Summary of what was delivered, plus optional follow-up improvements

## Change log
- 1.0.0 - Module 1 defined; Modules 2 and 3 planned.
