-- H2 (MODE=MySQL, DATABASE_TO_LOWER=TRUE) schema, mirrors docs/java-migration/mysql-ddl.sql
CREATE TABLE IF NOT EXISTS settings (
  `key` VARCHAR(191) NOT NULL,
  `value` CLOB NOT NULL,
  PRIMARY KEY (`key`)
);

CREATE TABLE IF NOT EXISTS accounts (
  id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  account VARCHAR(64) NOT NULL,
  mobile VARCHAR(32) NOT NULL DEFAULT '',
  phone VARCHAR(32) NOT NULL DEFAULT '',
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_accounts_account UNIQUE (account)
);
CREATE INDEX IF NOT EXISTS idx_accounts_mobile ON accounts(mobile);
CREATE INDEX IF NOT EXISTS idx_accounts_role_status ON accounts(role, status);

CREATE TABLE IF NOT EXISTS stores (
  id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  category VARCHAR(32) NOT NULL,
  address VARCHAR(255) NOT NULL,
  lat DECIMAL(10,6) NOT NULL,
  lng DECIMAL(10,6) NOT NULL,
  phone VARCHAR(32) NOT NULL DEFAULT '',
  notice CLOB NOT NULL,
  owner_id VARCHAR(64) NOT NULL,
  audit_status VARCHAR(16) NOT NULL DEFAULT 'approved',
  operation_status VARCHAR(16) NOT NULL DEFAULT 'active',
  operation_reason VARCHAR(500) NOT NULL DEFAULT '',
  operation_at TIMESTAMP(3) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_stores_owner ON stores(owner_id);
CREATE INDEX IF NOT EXISTS idx_stores_audit_operation ON stores(audit_status, operation_status);

CREATE TABLE IF NOT EXISTS batches (
  id VARCHAR(64) NOT NULL,
  `no` VARCHAR(64) NOT NULL,
  store_id VARCHAR(64) NOT NULL,
  category VARCHAR(32) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  pickup_start TIMESTAMP(3) NOT NULL,
  pickup_end TIMESTAMP(3) NOT NULL,
  total INT NOT NULL,
  sold INT NOT NULL DEFAULT 0,
  reserved INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'on_sale',
  published_at TIMESTAMP(3) NOT NULL,
  stop_reason VARCHAR(255) NULL,
  stopped_at TIMESTAMP(3) NULL,
  stock_logs CLOB NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_batches_store_status ON batches(store_id, status);
CREATE INDEX IF NOT EXISTS idx_batches_status_end ON batches(status, pickup_end);
CREATE INDEX IF NOT EXISTS idx_batches_store_start ON batches(store_id, pickup_start);

CREATE TABLE IF NOT EXISTS orders (
  id VARCHAR(64) NOT NULL,
  no VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  batch_id VARCHAR(64) NOT NULL,
  store_id VARCHAR(64) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  original_amount DECIMAL(10,2) NULL,
  discount DECIMAL(10,2) NULL,
  coupon_id VARCHAR(64) NULL,
  points_gained INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  pay_deadline TIMESTAMP(3) NOT NULL,
  paid_at TIMESTAMP(3) NULL,
  picked_at TIMESTAMP(3) NULL,
  pickup_code VARCHAR(16) NULL,
  qr_token VARCHAR(64) NULL,
  channel_tx VARCHAR(64) NULL,
  request_key VARCHAR(64) NULL,
  operator VARCHAR(100) NULL,
  cancel_reason VARCHAR(255) NULL,
  snap CLOB NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_orders_no UNIQUE (no),
  CONSTRAINT uk_orders_pickup_code UNIQUE (pickup_code)
);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_orders_store_status ON orders(store_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_status_deadline ON orders(status, pay_deadline);
CREATE INDEX IF NOT EXISTS idx_orders_status_paid ON orders(status, paid_at);
CREATE INDEX IF NOT EXISTS idx_orders_batch ON orders(batch_id);
CREATE INDEX IF NOT EXISTS idx_orders_coupon ON orders(coupon_id);

CREATE TABLE IF NOT EXISTS payment_intents (
  id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  coupon_id VARCHAR(64) NULL,
  channel VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP(3) NOT NULL,
  confirmed_at TIMESTAMP(3) NULL,
  failure_reason VARCHAR(255) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_intents_order UNIQUE (order_id)
);
CREATE INDEX IF NOT EXISTS idx_intents_status_created ON payment_intents(status, created_at);
CREATE INDEX IF NOT EXISTS idx_intents_user ON payment_intents(user_id);

CREATE TABLE IF NOT EXISTS refunds (
  id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  `type` VARCHAR(24) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'processing',
  restock TINYINT NOT NULL DEFAULT 0,
  reason VARCHAR(255) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  confirmed_at TIMESTAMP(3) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_refunds_order UNIQUE (order_id)
);
CREATE INDEX IF NOT EXISTS idx_refunds_status_created ON refunds(status, created_at);

CREATE TABLE IF NOT EXISTS aftersales (
  id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  evidence CLOB NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  deadline TIMESTAMP(3) NOT NULL,
  reply CLOB NULL,
  platform_note CLOB NULL,
  handled_at TIMESTAMP(3) NULL,
  resolved_at TIMESTAMP(3) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_aftersales_order_status ON aftersales(order_id, status);
CREATE INDEX IF NOT EXISTS idx_aftersales_status_deadline ON aftersales(status, deadline);

CREATE TABLE IF NOT EXISTS reviews (
  id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  store_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  user_name VARCHAR(100) NOT NULL,
  rating TINYINT NOT NULL,
  `text` CLOB NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_reviews_order UNIQUE (order_id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_store_created ON reviews(store_id, created_at);
CREATE INDEX IF NOT EXISTS idx_reviews_user ON reviews(user_id);

CREATE TABLE IF NOT EXISTS favorites (
  user_id VARCHAR(64) NOT NULL,
  store_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (user_id, store_id)
);
CREATE INDEX IF NOT EXISTS idx_favorites_store ON favorites(store_id);

CREATE TABLE IF NOT EXISTS wallets (
  user_id VARCHAR(64) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS coupons (
  id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  min_spend DECIMAL(10,2) NOT NULL DEFAULT 0,
  used TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL,
  used_at TIMESTAMP(3) NULL,
  order_id VARCHAR(64) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_coupons_user_used ON coupons(user_id, used, created_at);
CREATE INDEX IF NOT EXISTS idx_coupons_order ON coupons(order_id);

CREATE TABLE IF NOT EXISTS notices (
  id VARCHAR(64) NOT NULL,
  to_user VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body CLOB NOT NULL,
  link VARCHAR(255) NULL,
  `type` VARCHAR(24) NOT NULL DEFAULT 'info',
  `read` TINYINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_notices_to_created ON notices(to_user, created_at);
CREATE INDEX IF NOT EXISTS idx_notices_to_read ON notices(to_user, `read`);

CREATE TABLE IF NOT EXISTS logs (
  id VARCHAR(64) NOT NULL,
  `time` TIMESTAMP(3) NOT NULL,
  actor VARCHAR(100) NOT NULL,
  action VARCHAR(100) NOT NULL,
  detail CLOB NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_logs_time ON logs(`time`);
CREATE INDEX IF NOT EXISTS idx_logs_actor ON logs(actor);

CREATE TABLE IF NOT EXISTS drafts (
  id VARCHAR(64) NOT NULL,
  store_id VARCHAR(64) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  qty INT NOT NULL,
  category VARCHAR(32) NOT NULL,
  pickup_start TIMESTAMP(3) NOT NULL,
  pickup_end TIMESTAMP(3) NOT NULL,
  published TINYINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_drafts_store_published ON drafts(store_id, published);

CREATE TABLE IF NOT EXISTS applications (
  id VARCHAR(64) NOT NULL,
  store_name VARCHAR(100) NOT NULL,
  category VARCHAR(32) NOT NULL,
  applicant VARCHAR(100) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  address VARCHAR(255) NOT NULL,
  lat DECIMAL(10,6) NOT NULL,
  lng DECIMAL(10,6) NOT NULL,
  phone VARCHAR(32) NOT NULL DEFAULT '',
  materials CLOB NOT NULL,
  submitted_at TIMESTAMP(3) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  note CLOB NULL,
  audited_at TIMESTAMP(3) NULL,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_applications_status_submitted ON applications(status, submitted_at);
CREATE INDEX IF NOT EXISTS idx_applications_account ON applications(account_id);
