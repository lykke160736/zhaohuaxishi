-- 朝花惜食 MySQL 5.7 DDL 草案（第 0 阶段，仅供评审，不执行）
-- 目标库：MySQL 5.7.10 / InnoDB / utf8mb4 / 金额 DECIMAL / 时间 DATETIME(3)（UTC 存库）
--
-- ===== 可重复执行策略（默认 A：演示/测试库，完全重建）=====
-- 本脚本先 DROP 再 CREATE，因此可反复执行，但每次会把 16 张表清空。
-- 结构变更请勿直接改这里在生产执行；生产请把本文件作为 Flyway 的 V1__baseline.sql，
-- 只执行一次，后续变更写 V2、V3……（Flyway 自带 schema_version）。
-- 若坚持纯 SQL 手工执行：首次执行后再次执行会清空数据，属于预期行为。
--
-- ===== 与基线的有意差异（详见 risks.md）=====
-- 1) 金额：SQLite 整数分 → DECIMAL(10,2) 元；应用层负责 分↔元 精确换算，API 仍输出整数分。
-- 2) 时间：ISO 8601 TEXT → DATETIME(3)，按 UTC 存；应用层序列化回 ISO UTC 毫秒字符串。
-- 3) 为并发安全补 UNIQUE：payment_intents(order_id)、refunds(order_id)。
-- 4) 不添加物理外键（与基线一致，便于一次性导入）；逻辑关系见 db-schema.md。
-- 5) 所有表/列统一 utf8mb4_bin，保留 SQLite 的「大小写敏感等值比较」语义。
-- 6) 不使用 CHECK 约束（MySQL 5.7 解析但不强制执行），业务校验一律放在应用层。
--
-- ===== 金额换算约定 =====
-- 分 → 元：CAST(cents AS DECIMAL(10,2)) / 100，或 BigDecimal.valueOf(cents, 2)
-- 元 → 分：ROUND(amount * 100)，必须校验无小数残留

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `applications`;
DROP TABLE IF EXISTS `drafts`;
DROP TABLE IF EXISTS `logs`;
DROP TABLE IF EXISTS `notices`;
DROP TABLE IF EXISTS `coupons`;
DROP TABLE IF EXISTS `wallets`;
DROP TABLE IF EXISTS `favorites`;
DROP TABLE IF EXISTS `reviews`;
DROP TABLE IF EXISTS `aftersales`;
DROP TABLE IF EXISTS `refunds`;
DROP TABLE IF EXISTS `payment_intents`;
DROP TABLE IF EXISTS `orders`;
DROP TABLE IF EXISTS `batches`;
DROP TABLE IF EXISTS `stores`;
DROP TABLE IF EXISTS `accounts`;
DROP TABLE IF EXISTS `settings`;

-- ---------------------------------------------------------------------------
-- 1. settings
-- ---------------------------------------------------------------------------
CREATE TABLE `settings` (
  `key`   VARCHAR(191) NOT NULL COMMENT 'token_secret / center:<accountId>',
  `value` LONGTEXT     NOT NULL COMMENT '原样保存，不一定是 JSON（token_secret 为 hex 原文）',
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='键值设置';

-- ---------------------------------------------------------------------------
-- 2. accounts
-- ---------------------------------------------------------------------------
CREATE TABLE `accounts` (
  `id`            VARCHAR(64)  NOT NULL,
  `name`          VARCHAR(100) NOT NULL,
  `account`       VARCHAR(64)  NOT NULL COMMENT '登录账号，也允许 11 位手机号',
  `mobile`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '登录匹配用手机号',
  `phone`         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '展示用手机号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'scrypt$N$r$p$salt$hash，禁止改写',
  `role`          VARCHAR(16)  NOT NULL COMMENT 'customer/merchant/admin',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  `created_at`    DATETIME(3)  NOT NULL COMMENT 'UTC',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_accounts_account` (`account`),
  KEY `idx_accounts_mobile` (`mobile`),
  KEY `idx_accounts_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='账号';

-- ---------------------------------------------------------------------------
-- 3. stores
-- ---------------------------------------------------------------------------
CREATE TABLE `stores` (
  `id`               VARCHAR(64)   NOT NULL,
  `name`             VARCHAR(100)  NOT NULL,
  `category`         VARCHAR(32)   NOT NULL,
  `address`          VARCHAR(255)  NOT NULL,
  `lat`              DECIMAL(10,6) NOT NULL,
  `lng`              DECIMAL(10,6) NOT NULL,
  `phone`            VARCHAR(32)   NOT NULL DEFAULT '',
  `notice`           TEXT          NOT NULL,
  `owner_id`         VARCHAR(64)   NOT NULL,
  `audit_status`     VARCHAR(16)   NOT NULL DEFAULT 'approved' COMMENT '代码当前仅认 approved',
  `operation_status` VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT 'active/suspended',
  `operation_reason` VARCHAR(500)  NOT NULL DEFAULT '',
  `operation_at`     DATETIME(3)   NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stores_owner` (`owner_id`),
  KEY `idx_stores_audit_operation` (`audit_status`, `operation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='门店';

-- ---------------------------------------------------------------------------
-- 4. batches
-- ---------------------------------------------------------------------------
CREATE TABLE `batches` (
  `id`           VARCHAR(64)   NOT NULL,
  `no`           VARCHAR(64)   NOT NULL COMMENT '展示批次号，不唯一',
  `store_id`     VARCHAR(64)   NOT NULL,
  `category`     VARCHAR(32)   NOT NULL,
  `price`        DECIMAL(10,2) NOT NULL COMMENT '元；API 输出分',
  `pickup_start` DATETIME(3)   NOT NULL COMMENT 'UTC',
  `pickup_end`   DATETIME(3)   NOT NULL COMMENT 'UTC',
  `total`        INT           NOT NULL,
  `sold`         INT           NOT NULL DEFAULT 0,
  `reserved`     INT           NOT NULL DEFAULT 0,
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'on_sale' COMMENT 'on_sale/stopped',
  `published_at` DATETIME(3)   NOT NULL,
  `stop_reason`  VARCHAR(255)  NULL,
  `stopped_at`   DATETIME(3)   NULL,
  `stock_logs`   JSON          NOT NULL COMMENT '[{at,delta,by,after}]；5.7 JSON 不支持默认值，应用写入 []',
  PRIMARY KEY (`id`),
  KEY `idx_batches_store_status` (`store_id`, `status`),
  KEY `idx_batches_status_end` (`status`, `pickup_end`),
  KEY `idx_batches_store_start` (`store_id`, `pickup_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可售批次';

-- ---------------------------------------------------------------------------
-- 5. orders
-- ---------------------------------------------------------------------------
CREATE TABLE `orders` (
  `id`              VARCHAR(64)   NOT NULL,
  `no`              VARCHAR(64)   NOT NULL,
  `user_id`         VARCHAR(64)   NOT NULL,
  `batch_id`        VARCHAR(64)   NOT NULL,
  `store_id`        VARCHAR(64)   NOT NULL,
  `amount`          DECIMAL(10,2) NOT NULL COMMENT '实付，元；API 输出分',
  `original_amount` DECIMAL(10,2) NULL,
  `discount`        DECIMAL(10,2) NULL,
  `coupon_id`       VARCHAR(64)   NULL,
  `points_gained`   INT           NOT NULL DEFAULT 0,
  `status`          VARCHAR(24)   NOT NULL COMMENT 'pending_payment/to_pickup/picked/no_show/closed/refunding/refunded',
  `created_at`      DATETIME(3)   NOT NULL,
  `pay_deadline`    DATETIME(3)   NOT NULL,
  `paid_at`         DATETIME(3)   NULL,
  `picked_at`       DATETIME(3)   NULL,
  `pickup_code`     VARCHAR(16)   NULL,
  `qr_token`        VARCHAR(64)   NULL,
  `channel_tx`      VARCHAR(64)   NULL,
  `request_key`     VARCHAR(64)   NULL COMMENT '下单幂等键，当前仅留痕',
  `operator`        VARCHAR(100)  NULL,
  `cancel_reason`   VARCHAR(255)  NULL,
  `snap`            JSON          NOT NULL COMMENT '{storeName,category,price,address,pickupStart,pickupEnd}',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_no` (`no`),
  UNIQUE KEY `uk_orders_pickup_code` (`pickup_code`),
  KEY `idx_orders_user_created` (`user_id`, `created_at`),
  KEY `idx_orders_store_status` (`store_id`, `status`),
  KEY `idx_orders_status_deadline` (`status`, `pay_deadline`),
  KEY `idx_orders_status_paid` (`status`, `paid_at`),
  KEY `idx_orders_batch` (`batch_id`),
  KEY `idx_orders_coupon` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单';

-- ---------------------------------------------------------------------------
-- 6. payment_intents
-- ---------------------------------------------------------------------------
CREATE TABLE `payment_intents` (
  `id`             VARCHAR(64)   NOT NULL,
  `order_id`       VARCHAR(64)   NOT NULL,
  `user_id`        VARCHAR(64)   NOT NULL,
  `amount`         DECIMAL(10,2) NOT NULL,
  `coupon_id`      VARCHAR(64)   NULL,
  `channel`        VARCHAR(16)   NOT NULL COMMENT 'wechat/alipay',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/failed',
  `created_at`     DATETIME(3)   NOT NULL,
  `confirmed_at`   DATETIME(3)   NULL,
  `failure_reason` VARCHAR(255)  NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_intents_order` (`order_id`) COMMENT '一单一支付意图（迁移前须校验历史数据）',
  KEY `idx_intents_status_created` (`status`, `created_at`),
  KEY `idx_intents_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='支付意图';

-- ---------------------------------------------------------------------------
-- 7. refunds
-- ---------------------------------------------------------------------------
CREATE TABLE `refunds` (
  `id`           VARCHAR(64)   NOT NULL,
  `order_id`     VARCHAR(64)   NOT NULL,
  `type`         VARCHAR(24)   NOT NULL COMMENT 'normal/aftersale/platform/merchant_cancel',
  `amount`       DECIMAL(10,2) NOT NULL,
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'processing' COMMENT 'processing/success',
  `restock`      TINYINT(1)    NOT NULL DEFAULT 0,
  `reason`       VARCHAR(255)  NOT NULL,
  `created_at`   DATETIME(3)   NOT NULL,
  `confirmed_at` DATETIME(3)   NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refunds_order` (`order_id`) COMMENT '一单仅一笔退款（迁移前须校验历史数据）',
  KEY `idx_refunds_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='退款';

-- ---------------------------------------------------------------------------
-- 8. aftersales
-- ---------------------------------------------------------------------------
CREATE TABLE `aftersales` (
  `id`            VARCHAR(64)   NOT NULL,
  `order_id`      VARCHAR(64)   NOT NULL,
  `reason`        VARCHAR(255)  NOT NULL,
  `evidence`      TEXT          NOT NULL,
  `status`        VARCHAR(24)   NOT NULL COMMENT 'merchant_review/platform_review/agreed/rejected/resolved',
  `created_at`    DATETIME(3)   NOT NULL,
  `deadline`      DATETIME(3)   NOT NULL COMMENT '创建 +24h',
  `reply`         TEXT          NULL,
  `platform_note` TEXT          NULL,
  `handled_at`    DATETIME(3)   NULL,
  `resolved_at`   DATETIME(3)   NULL,
  PRIMARY KEY (`id`),
  KEY `idx_aftersales_order_status` (`order_id`, `status`),
  KEY `idx_aftersales_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='售后申请';

-- ---------------------------------------------------------------------------
-- 9. reviews
-- ---------------------------------------------------------------------------
CREATE TABLE `reviews` (
  `id`         VARCHAR(64)  NOT NULL,
  `order_id`   VARCHAR(64)  NOT NULL,
  `store_id`   VARCHAR(64)  NOT NULL,
  `user_id`    VARCHAR(64)  NOT NULL,
  `user_name`  VARCHAR(100) NOT NULL,
  `rating`     TINYINT      NOT NULL COMMENT '1-5',
  `text`       TEXT         NOT NULL,
  `created_at` DATETIME(3)  NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reviews_order` (`order_id`),
  KEY `idx_reviews_store_created` (`store_id`, `created_at`),
  KEY `idx_reviews_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='评价';

-- ---------------------------------------------------------------------------
-- 10. favorites
-- ---------------------------------------------------------------------------
CREATE TABLE `favorites` (
  `user_id`    VARCHAR(64) NOT NULL,
  `store_id`   VARCHAR(64) NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  PRIMARY KEY (`user_id`, `store_id`),
  KEY `idx_favorites_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='收藏';

-- ---------------------------------------------------------------------------
-- 11. wallets
-- ---------------------------------------------------------------------------
CREATE TABLE `wallets` (
  `user_id` VARCHAR(64) NOT NULL,
  `points`  INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='积分钱包';

-- ---------------------------------------------------------------------------
-- 12. coupons
-- ---------------------------------------------------------------------------
CREATE TABLE `coupons` (
  `id`         VARCHAR(64)   NOT NULL,
  `user_id`    VARCHAR(64)   NOT NULL,
  `amount`     DECIMAL(10,2) NOT NULL,
  `min_spend`  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `used`       TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at` DATETIME(3)   NOT NULL,
  `used_at`    DATETIME(3)   NULL,
  `order_id`   VARCHAR(64)   NULL,
  PRIMARY KEY (`id`),
  KEY `idx_coupons_user_used` (`user_id`, `used`, `created_at`),
  KEY `idx_coupons_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='优惠券';

-- ---------------------------------------------------------------------------
-- 13. notices
-- ---------------------------------------------------------------------------
CREATE TABLE `notices` (
  `id`         VARCHAR(64)  NOT NULL,
  `to_user`    VARCHAR(64)  NOT NULL,
  `title`      VARCHAR(200) NOT NULL,
  `body`       TEXT         NOT NULL,
  `link`       VARCHAR(255) NULL,
  `type`       VARCHAR(24)  NOT NULL DEFAULT 'info',
  `read`       TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at` DATETIME(3)  NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notices_to_created` (`to_user`, `created_at`),
  KEY `idx_notices_to_read` (`to_user`, `read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='站内通知';

-- ---------------------------------------------------------------------------
-- 14. logs
-- ---------------------------------------------------------------------------
CREATE TABLE `logs` (
  `id`     VARCHAR(64)  NOT NULL,
  `time`   DATETIME(3)  NOT NULL,
  `actor`  VARCHAR(100) NOT NULL COMMENT '展示名，非外键',
  `action` VARCHAR(100) NOT NULL,
  `detail` TEXT         NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_logs_time` (`time`),
  KEY `idx_logs_actor` (`actor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审计日志';

-- ---------------------------------------------------------------------------
-- 15. drafts
-- ---------------------------------------------------------------------------
CREATE TABLE `drafts` (
  `id`           VARCHAR(64)   NOT NULL,
  `store_id`     VARCHAR(64)   NOT NULL,
  `price`        DECIMAL(10,2) NOT NULL,
  `qty`          INT           NOT NULL,
  `category`     VARCHAR(32)   NOT NULL,
  `pickup_start` DATETIME(3)   NOT NULL,
  `pickup_end`   DATETIME(3)   NOT NULL,
  `published`    TINYINT(1)    NOT NULL DEFAULT 0,
  `updated_at`   DATETIME(3)   NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_drafts_store_published` (`store_id`, `published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商家草稿';

-- ---------------------------------------------------------------------------
-- 16. applications
-- ---------------------------------------------------------------------------
CREATE TABLE `applications` (
  `id`           VARCHAR(64)   NOT NULL,
  `store_name`   VARCHAR(100)  NOT NULL,
  `category`     VARCHAR(32)   NOT NULL,
  `applicant`    VARCHAR(100)  NOT NULL,
  `account_id`   VARCHAR(64)   NOT NULL,
  `address`      VARCHAR(255)  NOT NULL,
  `lat`          DECIMAL(10,6) NOT NULL,
  `lng`          DECIMAL(10,6) NOT NULL,
  `phone`        VARCHAR(32)   NOT NULL DEFAULT '',
  `materials`    TEXT          NOT NULL,
  `submitted_at` DATETIME(3)   NOT NULL,
  `status`       VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `note`         TEXT          NULL,
  `audited_at`   DATETIME(3)   NULL,
  PRIMARY KEY (`id`),
  KEY `idx_applications_status_submitted` (`status`, `submitted_at`),
  KEY `idx_applications_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='商家入驻申请';

SET FOREIGN_KEY_CHECKS = 1;

-- 说明：settings 不写入 token_secret，由应用首次启动生成并保存；
-- 若要保留现存令牌，可将 SQLite 中该值原样 INSERT 到 settings。
