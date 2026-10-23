# Module 2 - Student Subscription and Payment

Plans, orders, Razorpay payments, invoices, refunds, coupons, entitlements and the two-device limit.
Lives in `backend/src/main/java/com/lms/commerce` (same Spring Boot app as Module 1) and `frontend/src/student`.

## How it fits the system
```
Browser ──X-Device-Id on every request──► nginx ──► Spring Boot
                                                     ├─ identity (Module 1): login, JWT, RBAC, sessions
                                                     │     └─ DeviceGate hook ──► DeviceService (Module 2)
                                                     └─ commerce (Module 2)
                                                           orders ─► PaymentGateway ─► Razorpay
                                                           fulfilment ─► EntitlementService ─► user_entitlements
                                                                                                 ▲
                                          Module 1 EntitlementProvider reads it ─────────────────┘
```
- Module 2 trusts Module 1 for identity: it reads the bearer token and calls `can(user, action, resource)`.
  New permissions added to the matrix: `finance:manage`, `device:read`, `device:manage`.
- Module 1 never imports Module 2. It exposes the `DeviceGate` interface; `DeviceService` implements it.
- Module 3 (courses) will replace `purchasable_courses` and call `GET /commerce/entitlements/check`.

## Rules implemented
| Requirement | Behaviour |
|---|---|
| Plans | Free, Monthly, Annual, Corporate (self-serve) and Enterprise (contact sales). Fixed-term: buying again extends. |
| Orders | The server prices every order; the browser only names an item and a coupon. |
| Payment | Razorpay Checkout (UPI, cards, net banking, international cards). Confirmed by signature check and by webhook; fulfilment runs once. |
| Invoices | One per paid order, printable from the UI. |
| Refunds | Learner requests within 7 days (`lms.commerce.refund-window-days`); finance approves (money returns via Razorpay) or rejects. Approval removes exactly what that order granted. |
| Coupons | Percent or flat, per-item-type, expiry, usage cap, one use per learner. |
| Entitlements | `COURSE:<ref>` from purchases; `PLAN:<code>` and `FEATURE:ALL_COURSES` from paid plans. |
| Devices | Max 2 active per learner (`lms.commerce.max-devices`). Registered at login; third device refused with `DEVICE_LIMIT_REACHED`; deactivate / replace / admin reset free a slot and kill that device's sessions at once. Full audit trail. |

## Database (migration `V2__commerce.sql`)
`plans`, `purchasable_courses`, `coupons`, `coupon_redemptions`, `orders`, `invoices`, `refunds`, `subscriptions`,
`devices`, `device_events`; `user_sessions.device_id` added. Money is integer paise. Device ids are stored as SHA-256 only.

## API (`/api/v1/commerce`)
Public: `GET /plans`, `GET /courses`, `POST /webhooks/razorpay` (HMAC-verified).
Learner: `GET /subscription`, `GET /entitlements/check?type&ref`, `POST /orders`, `POST /orders/{id}/confirm`,
`GET /orders`, `GET /orders/{id}`, `POST /coupons/validate`, `GET /coupons/mine`, `GET /invoices[/{id}]`,
`POST /orders/{id}/refund`, `GET /refunds`, `GET /devices`, `GET /devices/history`, `POST /devices/validate`,
`POST /devices/{id}/deactivate`, `POST /devices/{id}/replace`.
Admin (`/admin`): `GET|PUT /plans`, `GET|POST /coupons`, `PUT /coupons/{id}/active`, `GET /orders`, `GET /refunds`,
`POST /refunds/{id}/approve|reject`, `GET /devices/events`, `GET /users/{id}/devices`, `POST /users/{id}/devices/reset`.

Device/licence validation: send `X-Device-Id`, call `POST /devices/validate` -> `{valid, reason, ...}`.
Reasons: `OK`, `DEVICE_NOT_REGISTERED`, `DEVICE_DEACTIVATED`, `DEVICE_REPLACED`, `DEVICE_ID_REQUIRED`, `NOT_APPLICABLE`.

## Razorpay setup
1. Create a Razorpay account and copy the **test** Key Id and Key Secret (Settings -> API Keys).
2. Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` (docker compose passes them through), restart the backend.
3. Webhook: add `https://<your-domain>/api/v1/commerce/webhooks/razorpay` with events `payment.captured` and `order.paid`,
   and set the same secret as `RAZORPAY_WEBHOOK_SECRET`.
4. International cards must be enabled by Razorpay on your account; test with their test card numbers.
Without keys, free plans and 100%-discounted orders still work; paid orders answer `503 PAYMENTS_NOT_CONFIGURED`.

## UI
Header dropdown **Student Subscription and Payment** (Learner role): Plans & Pricing, My Subscription, My Orders,
Invoices & Refunds, Coupons, My Devices. Public `/pricing`. Sidebar **Billing & Devices** for Super Admin, Finance Admin
(refunds, coupons, orders, plans, device audit) and Support Agent (device audit and reset).

## Not included yet (follow-ups)
- Automatic renewal (Razorpay Subscriptions API); plans are one-time fixed-term purchases.
- GST / tax breakdown on invoices, partial refunds, multi-currency pricing.
- Corporate/Enterprise seat management for organizations.
- SMS/email receipts. Seed prices in `V2__commerce.sql` are placeholders.
- Native mobile apps must send a stable `X-Device-Id` (e.g. a generated install id) to be admitted.
