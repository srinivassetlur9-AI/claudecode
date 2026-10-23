-- Module 1: Identity & User Management
-- Every table carries id, created_at, updated_at, created_by. Audited records are soft-deleted (deleted_at).

CREATE TABLE organizations (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  type        VARCHAR(20)  NOT NULL,              -- PLATFORM | ORGANIZATION
  status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  created_by  BIGINT NULL,
  deleted_at  DATETIME(6)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id BIGINT NULL,
  email           VARCHAR(254) NOT NULL,
  mobile          VARCHAR(20)  NULL,
  password_hash   VARCHAR(100) NOT NULL,
  first_name      VARCHAR(100) NOT NULL,
  last_name       VARCHAR(100) NOT NULL,
  status          VARCHAR(20)  NOT NULL,          -- PENDING | ACTIVE | DISABLED
  email_verified  TINYINT(1)   NOT NULL DEFAULT 0,
  mobile_verified TINYINT(1)   NOT NULL DEFAULT 0,
  failed_attempts INT          NOT NULL DEFAULT 0,
  locked_until    DATETIME(6)  NULL,
  last_login_at   DATETIME(6)  NULL,
  language        VARCHAR(10)  NOT NULL DEFAULT 'en',
  created_at      DATETIME(6)  NOT NULL,
  updated_at      DATETIME(6)  NOT NULL,
  created_by      BIGINT NULL,
  deleted_at      DATETIME(6)  NULL,
  CONSTRAINT uq_users_email  UNIQUE (email),
  CONSTRAINT uq_users_mobile UNIQUE (mobile),
  CONSTRAINT fk_users_org FOREIGN KEY (organization_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_profiles (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  headline   VARCHAR(200) NULL,
  bio        TEXT NULL,
  avatar_url VARCHAR(500) NULL,
  country    VARCHAR(2)   NULL,
  timezone   VARCHAR(60)  NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  created_by BIGINT NULL,
  CONSTRAINT uq_profile_user UNIQUE (user_id),
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  role_code  VARCHAR(30) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by BIGINT NULL,
  CONSTRAINT uq_user_role UNIQUE (user_id, role_code),
  CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Single-use, expiring secrets: email verification links, mobile OTPs, password-reset links. Only SHA-256 hashes are stored.
CREATE TABLE verification_tokens (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  purpose    VARCHAR(20) NOT NULL,                -- EMAIL_VERIFY | MOBILE_OTP | PASSWORD_RESET
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at    DATETIME(6) NULL,
  attempts   INT         NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by BIGINT NULL,
  INDEX idx_vt_hash (token_hash),
  INDEX idx_vt_user_purpose (user_id, purpose),
  CONSTRAINT fk_vt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per login = one device/session. Module 2 reads this to enforce device limits.
CREATE TABLE user_sessions (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT NOT NULL,
  refresh_token_hash VARCHAR(64) NOT NULL,
  device_name        VARCHAR(150) NULL,
  user_agent         VARCHAR(500) NULL,
  ip_address         VARCHAR(45)  NULL,
  last_seen_at       DATETIME(6) NOT NULL,
  expires_at         DATETIME(6) NOT NULL,
  revoked_at         DATETIME(6) NULL,
  revoked_reason     VARCHAR(30) NULL,            -- LOGOUT | ROTATED | REUSE_DETECTED | PASSWORD_CHANGE | ADMIN
  created_at         DATETIME(6) NOT NULL,
  updated_at         DATETIME(6) NOT NULL,
  created_by         BIGINT NULL,
  CONSTRAINT uq_session_refresh UNIQUE (refresh_token_hash),
  INDEX idx_session_user (user_id),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Hook for Module 2 (Subscription, Commerce): intentionally empty and unused by Module 1 logic.
CREATE TABLE user_entitlements (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  entitlement_type VARCHAR(30)  NOT NULL,         -- COURSE | PLAN | FEATURE
  resource_ref     VARCHAR(100) NOT NULL,
  source           VARCHAR(50)  NULL,
  valid_from       DATETIME(6)  NULL,
  valid_until      DATETIME(6)  NULL,
  created_at       DATETIME(6)  NOT NULL,
  updated_at       DATETIME(6)  NOT NULL,
  created_by       BIGINT NULL,
  INDEX idx_ent_user (user_id),
  CONSTRAINT fk_ent_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO organizations (name, type, status, created_at, updated_at)
VALUES ('Platform', 'PLATFORM', 'ACTIVE', NOW(6), NOW(6));
