-- ─────────────────────────────────────────────────────────────────────────────
-- V8 — Admin panel support: user fields, audit_logs table, indexes
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. New columns on users ───────────────────────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at  TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS login_count    INT     NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active      BOOLEAN NOT NULL DEFAULT TRUE;

-- ── 2. Performance indexes for admin queries ──────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_email      ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);
CREATE INDEX IF NOT EXISTS idx_users_is_active  ON users (is_active);

-- ── 3. Extend statements status check to include FAILED ───────────────────────
ALTER TABLE statements DROP CONSTRAINT IF EXISTS statements_status_check;
ALTER TABLE statements ADD CONSTRAINT statements_status_check
    CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'));

-- ── 4. Audit logs table ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    username    VARCHAR(255),
    user_id     BIGINT,
    ip_address  VARCHAR(45),
    details     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_username   ON audit_logs (username);
CREATE INDEX IF NOT EXISTS idx_audit_logs_event_type ON audit_logs (event_type);
