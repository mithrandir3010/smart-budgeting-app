CREATE TABLE system_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT         NOT NULL DEFAULT '',
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('maintenance_mode', 'false'),
    ('announcement',     ''),
    ('disabled_banks',   '');
