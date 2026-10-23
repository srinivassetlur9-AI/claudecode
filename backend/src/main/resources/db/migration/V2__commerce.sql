-- Module 2: Student Subscription, Payment & Device Management
-- Money is stored as integer paise (1/100 INR) to avoid rounding errors. Every table carries id, created_at, updated_at, created_by.

CREATE TABLE plans (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(30)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  plan_type     VARCHAR(20)  NOT NULL,              -- FREE | MONTHLY | ANNUAL | CORPORATE | ENTERPRISE
  price_paise   BIGINT       NOT NULL DEFAULT 0,
  currency      VARCHAR(3)   NOT NULL DEFAULT 'INR',
  duration_days INT          NULL,                  -- NULL = never expires (free plan)
  self_serve    TINYINT(1)   NOT NULL DEFAULT 1,    -- 0 = contact sales (enterprise)
  grants_all_courses TINYINT(1) NOT NULL DEFAULT 0,
  description   VARCHAR(300) NULL,
  features      TEXT         NULL,                  -- features separated by |
  sort_order    INT          NOT NULL DEFAULT 0,
  active        TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  created_by    BIGINT NULL,
  CONSTRAINT uq_plans_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stand-in catalogue until Module 3 (Course Management) owns courses. course_ref is what entitlements point at.
CREATE TABLE purchasable_courses (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_ref  VARCHAR(60)  NOT NULL,
  title       VARCHAR(200) NOT NULL,
  price_paise BIGINT       NOT NULL,
  currency    VARCHAR(3)   NOT NULL DEFAULT 'INR',
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  created_by  BIGINT NULL,
  CONSTRAINT uq_course_ref UNIQUE (course_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupons (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(40) NOT NULL,
  discount_type    VARCHAR(10) NOT NULL,            -- PERCENT | FLAT
  discount_value   BIGINT      NOT NULL,            -- percent (1-100) or paise
  applies_to       VARCHAR(10) NOT NULL DEFAULT 'ALL', -- ALL | PLAN | COURSE
  max_redemptions  INT         NULL,
  redeemed_count   INT         NOT NULL DEFAULT 0,
  valid_from       DATETIME(6) NULL,
  valid_until      DATETIME(6) NULL,
  active           TINYINT(1)  NOT NULL DEFAULT 1,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  created_by       BIGINT NULL,
  CONSTRAINT uq_coupon_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no             VARCHAR(30)  NOT NULL,
  user_id              BIGINT       NOT NULL,
  item_type            VARCHAR(10)  NOT NULL,       -- PLAN | COURSE
  item_ref             VARCHAR(60)  NOT NULL,       -- plan code or course_ref
  title                VARCHAR(200) NOT NULL,
  subtotal_paise       BIGINT       NOT NULL,
  discount_paise       BIGINT       NOT NULL DEFAULT 0,
  total_paise          BIGINT       NOT NULL,
  currency             VARCHAR(3)   NOT NULL,
  coupon_code          VARCHAR(40)  NULL,
  status               VARCHAR(12)  NOT NULL,       -- CREATED | PAID | FAILED | REFUNDED
  razorpay_order_id    VARCHAR(60)  NULL,
  razorpay_payment_id  VARCHAR(60)  NULL,
  paid_at              DATETIME(6)  NULL,
  created_at           DATETIME(6)  NOT NULL,
  updated_at           DATETIME(6)  NOT NULL,
  created_by           BIGINT NULL,
  CONSTRAINT uq_order_no UNIQUE (order_no),
  INDEX idx_orders_user (user_id),
  INDEX idx_orders_rzp (razorpay_order_id),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupon_redemptions (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  coupon_id  BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  order_id   BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by BIGINT NULL,
  CONSTRAINT uq_redemption_user UNIQUE (coupon_id, user_id),  -- one use per learner
  CONSTRAINT fk_red_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id),
  CONSTRAINT fk_red_order  FOREIGN KEY (order_id)  REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoices (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_no    VARCHAR(30)  NOT NULL,
  order_id      BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  amount_paise  BIGINT       NOT NULL,
  currency      VARCHAR(3)   NOT NULL,
  billing_name  VARCHAR(200) NOT NULL,
  billing_email VARCHAR(254) NOT NULL,
  description   VARCHAR(200) NOT NULL,
  issued_at     DATETIME(6)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  created_by    BIGINT NULL,
  CONSTRAINT uq_invoice_no UNIQUE (invoice_no),
  CONSTRAINT uq_invoice_order UNIQUE (order_id),
  INDEX idx_invoice_user (user_id),
  CONSTRAINT fk_invoice_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refunds (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id           BIGINT       NOT NULL,
  user_id            BIGINT       NOT NULL,
  amount_paise       BIGINT       NOT NULL,
  reason             VARCHAR(500) NOT NULL,
  status             VARCHAR(12)  NOT NULL,         -- REQUESTED | PROCESSED | REJECTED | FAILED
  razorpay_refund_id VARCHAR(60)  NULL,
  decided_by         BIGINT       NULL,
  decided_at         DATETIME(6)  NULL,
  decision_note      VARCHAR(500) NULL,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  created_by         BIGINT NULL,
  INDEX idx_refund_order (order_id),
  INDEX idx_refund_user (user_id),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE subscriptions (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT      NOT NULL,
  plan_id    BIGINT      NOT NULL,
  order_id   BIGINT      NULL,
  status     VARCHAR(12) NOT NULL,                  -- ACTIVE | CANCELLED | EXPIRED | REFUNDED
  starts_at  DATETIME(6) NOT NULL,
  ends_at    DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by BIGINT NULL,
  INDEX idx_sub_user (user_id),
  CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_sub_plan FOREIGN KEY (plan_id) REFERENCES plans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Registered learner devices. Only a SHA-256 of the client-generated device id is stored.
CREATE TABLE devices (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id             BIGINT       NOT NULL,
  device_key_hash     VARCHAR(64)  NOT NULL,
  name                VARCHAR(150) NOT NULL,
  platform            VARCHAR(100) NULL,
  status              VARCHAR(12)  NOT NULL,        -- ACTIVE | DEACTIVATED | REPLACED
  registered_at       DATETIME(6)  NOT NULL,
  last_seen_at        DATETIME(6)  NOT NULL,
  last_ip             VARCHAR(45)  NULL,
  deactivated_at      DATETIME(6)  NULL,
  deactivation_reason VARCHAR(200) NULL,
  replaced_by         BIGINT       NULL,
  created_at          DATETIME(6)  NOT NULL,
  updated_at          DATETIME(6)  NOT NULL,
  created_by          BIGINT NULL,
  CONSTRAINT uq_device_user_key UNIQUE (user_id, device_key_hash),
  INDEX idx_device_user_status (user_id, status),
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only audit trail of everything that happens to a device.
CREATE TABLE device_events (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NOT NULL,
  device_id  BIGINT       NULL,
  event_type VARCHAR(20)  NOT NULL,                 -- REGISTERED | LOGIN | BLOCKED | DEACTIVATED | REPLACED | REACTIVATED | ADMIN_RESET
  detail     VARCHAR(300) NULL,
  ip_address VARCHAR(45)  NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  created_by BIGINT NULL,
  INDEX idx_devevt_user (user_id),
  CONSTRAINT fk_devevt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE user_sessions ADD COLUMN device_id BIGINT NULL;
CREATE INDEX idx_session_device ON user_sessions (device_id);

-- ---- seed data (prices are placeholders; change them in Plans admin or here before launch) ----
INSERT INTO plans (code, name, plan_type, price_paise, duration_days, self_serve, grants_all_courses, description, features, sort_order, created_at, updated_at) VALUES
 ('FREE',       'Free',       'FREE',       0,       NULL, 1, 0, 'Get started at no cost',      'Access to free courses|Limited AI tutor|Community support|Basic certificates', 1, NOW(6), NOW(6)),
 ('MONTHLY',    'Monthly',    'MONTHLY',    29900,   30,   1, 1, 'Full access, billed monthly', 'Full course library|AI tutor (advanced)|Quizzes & practice tests|Certificates|Up to 2 devices', 2, NOW(6), NOW(6)),
 ('ANNUAL',     'Annual',     'ANNUAL',     249900,  365,  1, 1, 'Best value, billed yearly',   'Everything in Monthly|Offline learning|Priority support|Live classes & webinars', 3, NOW(6), NOW(6)),
 ('CORPORATE',  'Corporate',  'CORPORATE',  999900,  365,  1, 1, 'For teams and organizations', 'Everything in Annual|Admin controls|Bulk enrollments|Analytics & reporting', 4, NOW(6), NOW(6)),
 ('ENTERPRISE', 'Enterprise', 'ENTERPRISE', 0,       NULL, 0, 1, 'Custom for large organizations', 'Dedicated learning portal|SSO & custom integrations|Custom content|Dedicated support', 5, NOW(6), NOW(6));

INSERT INTO purchasable_courses (course_ref, title, price_paise, currency, created_at, updated_at) VALUES
 ('gen-ai-everyone',   'Generative AI for Everyone',       59900, 'INR', NOW(6), NOW(6)),
 ('machine-learning',  'Machine Learning',                 69900, 'INR', NOW(6), NOW(6)),
 ('cloud-computing',   'Cloud Computing',                  49900, 'INR', NOW(6), NOW(6));

INSERT INTO coupons (code, discount_type, discount_value, applies_to, active, created_at, updated_at)
VALUES ('WELCOME10', 'PERCENT', 10, 'ALL', 1, NOW(6), NOW(6));
