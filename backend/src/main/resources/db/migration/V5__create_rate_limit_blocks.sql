-- Auth ve upload rate-limit bloklarını yeniden başlatmada kaybetmemek için kalıcı tablo.
-- API bloklarını (60/dak) buraya kaydetmiyoruz; pencere çok kısa, overhead değmez.
CREATE TABLE rate_limit_blocks (
    bucket_key    VARCHAR(100) PRIMARY KEY,
    blocked_until TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_rlb_expiry ON rate_limit_blocks (blocked_until);
