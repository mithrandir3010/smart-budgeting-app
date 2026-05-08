-- ─────────────────────────────────────────────────────────────────────────────
-- V2 — Add bank_name to statements for per-bank duplicate detection
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE statements
    ADD COLUMN bank_name VARCHAR(50);
