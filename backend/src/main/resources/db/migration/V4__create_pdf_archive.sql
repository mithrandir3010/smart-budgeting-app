-- ─────────────────────────────────────────────────────────────────────────────
-- V4 — Replace per-user pdf_content with anonymous pdf_archive table
-- ─────────────────────────────────────────────────────────────────────────────

-- V3 added pdf_content to statements; move responsibility to the archive table
ALTER TABLE statements
    DROP COLUMN IF EXISTS pdf_content;

CREATE TABLE pdf_archive (
    id          BIGSERIAL PRIMARY KEY,
    bank_name   VARCHAR(50),
    upload_date DATE        NOT NULL,
    pdf_content BYTEA       NOT NULL
);
