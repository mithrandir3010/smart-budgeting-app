-- ─────────────────────────────────────────────────────────────────────────────
-- V3 — Store raw PDF bytes for pipeline improvement
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE statements
    ADD COLUMN pdf_content BYTEA;
