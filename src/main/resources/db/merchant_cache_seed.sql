-- merchant_cache başlangıç verileri — PostgreSQL
-- Uygulama MerchantCacheSeeder ile otomatik seed yapar;
-- bu script manuel müdahale veya migration aracı içindir.
--
-- Kullanım:
--   psql -U postgres -d smart_budget_db -f merchant_cache_seed.sql

INSERT INTO merchant_cache (pattern, category, is_subscription, hit_count, created_at)
VALUES
  -- Market / Gıda
  ('Migros',        'Market',    false, 0, NOW()),
  ('BİM',           'Market',    false, 0, NOW()),
  ('A101',          'Market',    false, 0, NOW()),
  ('ŞOK',           'Market',    false, 0, NOW()),
  ('Carrefour',     'Market',    false, 0, NOW()),
  ('Hakmar',        'Market',    false, 0, NOW()),

  -- Kafe
  ('Starbucks',       'Kafe',    false, 0, NOW()),
  ('Kahve Dünyası',   'Kafe',    false, 0, NOW()),
  ('Gloria Jeans',    'Kafe',    false, 0, NOW()),

  -- Restoran
  ('McDonald''s',   'Restoran',  false, 0, NOW()),
  ('KFC',           'Restoran',  false, 0, NOW()),
  ('Burger King',   'Restoran',  false, 0, NOW()),
  ('Popeyes',       'Restoran',  false, 0, NOW()),
  ('Subway',        'Restoran',  false, 0, NOW()),

  -- Ulaşım
  ('Uber',          'Ulaşım',    false, 0, NOW()),
  ('BiTaksi',       'Ulaşım',    false, 0, NOW()),
  ('İETT',          'Ulaşım',    false, 0, NOW()),
  ('Marmaray',      'Ulaşım',    false, 0, NOW()),

  -- Akaryakıt
  ('Shell',         'Akaryakıt', false, 0, NOW()),
  ('Opet',          'Akaryakıt', false, 0, NOW()),
  ('BP',            'Akaryakıt', false, 0, NOW()),
  ('Total',         'Akaryakıt', false, 0, NOW()),

  -- Fatura
  ('Turkcell',      'Fatura',    false, 0, NOW()),
  ('Vodafone',      'Fatura',    false, 0, NOW()),
  ('Türk Telekom',  'Fatura',    false, 0, NOW()),

  -- Eğlence / Abonelik
  ('Netflix',       'Eğlence',   true,  0, NOW()),
  ('Spotify',       'Eğlence',   true,  0, NOW()),
  ('YouTube',       'Eğlence',   true,  0, NOW()),
  ('Disney+',       'Eğlence',   true,  0, NOW()),
  ('Amazon Prime',  'Eğlence',   true,  0, NOW()),
  ('Todtv.com.tr',  'Eğlence',   true,  0, NOW()),

  -- Teknoloji
  ('Apple',         'Teknoloji', false, 0, NOW()),
  ('iCloud',        'Teknoloji', true,  0, NOW()),

  -- Giyim
  ('Zara',          'Giyim',     false, 0, NOW()),
  ('LC Waikiki',    'Giyim',     false, 0, NOW()),
  ('DeFacto',       'Giyim',     false, 0, NOW()),

  -- Eğitim
  ('Udemy',         'Eğitim',    true,  0, NOW())

ON CONFLICT (pattern) DO NOTHING;
