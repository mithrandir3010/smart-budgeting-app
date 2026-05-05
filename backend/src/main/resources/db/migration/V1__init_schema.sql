-- ─────────────────────────────────────────────────────────────────────────────
-- V1 — Initial schema
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(255) NOT NULL UNIQUE,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    role           VARCHAR(255) NOT NULL DEFAULT 'ROLE_USER',
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    monthly_budget NUMERIC(12, 2),
    created_at     TIMESTAMP(6) NOT NULL
);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id     BIGINT       NOT NULL UNIQUE REFERENCES users (id)
);

CREATE TABLE statements (
    id              BIGSERIAL PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL CHECK (status IN ('PENDING', 'PROCESSED')),
    upload_date     DATE         NOT NULL,
    period_start    DATE,
    period_end      DATE,
    sha256_checksum VARCHAR(64),
    user_id         BIGINT       NOT NULL REFERENCES users (id)
);

CREATE TABLE transactions (
    id                  BIGSERIAL PRIMARY KEY,
    amount              NUMERIC(38, 2) NOT NULL,
    date                DATE           NOT NULL,
    description         VARCHAR(255),
    category            VARCHAR(255),
    category_enum       VARCHAR(255)   CHECK (category_enum IN (
                            'FOOD','TRANSPORT','HOUSING','SHOPPING',
                            'HEALTH','EDUCATION','ENTERTAINMENT','OTHER'
                        )),
    currency            VARCHAR(3),
    is_installment      BOOLEAN        NOT NULL DEFAULT FALSE,
    is_subscription     BOOLEAN        NOT NULL DEFAULT FALSE,
    current_installment INT,
    total_installments  INT,
    statement_id        BIGINT         REFERENCES statements (id),
    user_id             BIGINT         NOT NULL REFERENCES users (id)
);

CREATE TABLE budget_limits (
    id           BIGSERIAL PRIMARY KEY,
    category     VARCHAR(255)   NOT NULL,
    limit_amount NUMERIC(14, 2) NOT NULL,
    user_id      BIGINT         NOT NULL REFERENCES users (id),
    UNIQUE (user_id, category)
);

CREATE TABLE email_verification_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id    BIGINT       NOT NULL REFERENCES users (id)
);

CREATE TABLE merchant_cache (
    id              BIGSERIAL PRIMARY KEY,
    pattern         VARCHAR(255) NOT NULL UNIQUE,
    category        VARCHAR(50)  NOT NULL,
    is_subscription BOOLEAN      NOT NULL DEFAULT FALSE,
    hit_count       INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_merchant_pattern ON merchant_cache (pattern);
