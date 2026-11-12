-- =====================================================================
-- DataTalk AI - Phase 4 Migration (device health, fees, refunds)
-- Run in the Neon SQL Editor as the database OWNER role (neondb_owner),
-- AFTER phase3-migration.sql. Idempotent: safe to run more than once
-- (re-running adds health snapshots for new days and fees/refunds for new transactions).
-- Restart the application afterwards so the reporting catalog picks up the new tables.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. device.device_health_metric - daily health snapshot per device
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device.device_health_metric (
    metric_id        SERIAL PRIMARY KEY,
    device_id        INTEGER       NOT NULL REFERENCES device.device(device_id) ON DELETE CASCADE,
    recorded_time    TIMESTAMP     NOT NULL,
    battery_level    INTEGER       CHECK (battery_level BETWEEN 0 AND 100),
    signal_strength  INTEGER       NOT NULL CHECK (signal_strength BETWEEN 0 AND 100),
    uptime_pct       DECIMAL(5, 2) NOT NULL CHECK (uptime_pct BETWEEN 0 AND 100),
    error_count      INTEGER       NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    health_status    VARCHAR(20)   NOT NULL CHECK (health_status IN ('HEALTHY', 'WARNING', 'CRITICAL')),
    created_date     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, recorded_time)
);

CREATE INDEX IF NOT EXISTS idx_device_health_recorded_time ON device.device_health_metric(recorded_time);
CREATE INDEX IF NOT EXISTS idx_device_health_status ON device.device_health_metric(health_status);

COMMENT ON TABLE device.device_health_metric IS 'Daily device health snapshot (battery, signal, uptime, errors)';
COMMENT ON COLUMN device.device_health_metric.device_id IS 'Device the snapshot belongs to';
COMMENT ON COLUMN device.device_health_metric.recorded_time IS 'When the health snapshot was taken';
COMMENT ON COLUMN device.device_health_metric.battery_level IS 'Battery level percent (NULL for mains-powered devices)';
COMMENT ON COLUMN device.device_health_metric.signal_strength IS 'Network signal strength percent (0-100)';
COMMENT ON COLUMN device.device_health_metric.uptime_pct IS 'Percent of the day the device was online';
COMMENT ON COLUMN device.device_health_metric.error_count IS 'Number of device errors during the day';
COMMENT ON COLUMN device.device_health_metric.health_status IS 'HEALTHY, WARNING or CRITICAL';

-- ---------------------------------------------------------------------
-- 2. txn.transaction_fee - fees charged per payment transaction
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS txn.transaction_fee (
    fee_id          SERIAL PRIMARY KEY,
    transaction_id  INTEGER        NOT NULL REFERENCES txn.payment_transaction(transaction_id) ON DELETE RESTRICT,
    customer_id     INTEGER        NOT NULL REFERENCES corp.customer(customer_id) ON DELETE RESTRICT,
    device_id       INTEGER        NOT NULL REFERENCES device.device(device_id) ON DELETE RESTRICT,
    fee_type        VARCHAR(20)    NOT NULL CHECK (fee_type IN ('INTERCHANGE', 'SCHEME', 'PROCESSING', 'GATEWAY')),
    fee_amount      DECIMAL(15, 2) NOT NULL CHECK (fee_amount >= 0),
    fee_time        TIMESTAMP      NOT NULL,
    created_date    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (transaction_id, fee_type)
);

CREATE INDEX IF NOT EXISTS idx_transaction_fee_customer_id ON txn.transaction_fee(customer_id);
CREATE INDEX IF NOT EXISTS idx_transaction_fee_device_id ON txn.transaction_fee(device_id);
CREATE INDEX IF NOT EXISTS idx_transaction_fee_fee_time ON txn.transaction_fee(fee_time);

COMMENT ON TABLE txn.transaction_fee IS 'Fees charged on payment transactions (one row per fee type)';
COMMENT ON COLUMN txn.transaction_fee.transaction_id IS 'Payment transaction the fee was charged on';
COMMENT ON COLUMN txn.transaction_fee.fee_type IS 'INTERCHANGE (card issuer), SCHEME (card network), PROCESSING (acquirer), GATEWAY (online/QR)';
COMMENT ON COLUMN txn.transaction_fee.fee_amount IS 'Fee amount in base currency';
COMMENT ON COLUMN txn.transaction_fee.fee_time IS 'When the fee was charged (transaction time)';

-- ---------------------------------------------------------------------
-- 3. txn.refund - refunds of payment transactions
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS txn.refund (
    refund_id       SERIAL PRIMARY KEY,
    transaction_id  INTEGER        NOT NULL UNIQUE REFERENCES txn.payment_transaction(transaction_id) ON DELETE RESTRICT,
    customer_id     INTEGER        NOT NULL REFERENCES corp.customer(customer_id) ON DELETE RESTRICT,
    device_id       INTEGER        NOT NULL REFERENCES device.device(device_id) ON DELETE RESTRICT,
    refund_amount   DECIMAL(15, 2) NOT NULL CHECK (refund_amount > 0),
    refund_reason   VARCHAR(30)    NOT NULL
        CHECK (refund_reason IN ('CUSTOMER_REQUEST', 'DUPLICATE_CHARGE', 'PRODUCT_RETURN', 'SERVICE_ISSUE', 'FRAUD')),
    refund_status   VARCHAR(20)    NOT NULL CHECK (refund_status IN ('REQUESTED', 'APPROVED', 'COMPLETED', 'REJECTED')),
    requested_time  TIMESTAMP      NOT NULL,
    processed_time  TIMESTAMP,
    created_date    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refund_customer_id ON txn.refund(customer_id);
CREATE INDEX IF NOT EXISTS idx_refund_device_id ON txn.refund(device_id);
CREATE INDEX IF NOT EXISTS idx_refund_requested_time ON txn.refund(requested_time);
CREATE INDEX IF NOT EXISTS idx_refund_status ON txn.refund(refund_status);

COMMENT ON TABLE txn.refund IS 'Refunds (full or partial) of payment transactions';
COMMENT ON COLUMN txn.refund.transaction_id IS 'Original payment transaction';
COMMENT ON COLUMN txn.refund.refund_amount IS 'Refunded amount in base currency';
COMMENT ON COLUMN txn.refund.refund_reason IS 'Why the refund was requested';
COMMENT ON COLUMN txn.refund.refund_status IS 'REQUESTED, APPROVED, COMPLETED or REJECTED';
COMMENT ON COLUMN txn.refund.requested_time IS 'When the refund was requested';
COMMENT ON COLUMN txn.refund.processed_time IS 'When the refund was approved/completed/rejected (NULL while requested)';

-- ---------------------------------------------------------------------
-- 4. Data - derived from the existing devices and transactions
-- ---------------------------------------------------------------------
-- Health snapshots for the last 30 days (devices in ERROR/MAINTENANCE get worse metrics)
INSERT INTO device.device_health_metric
       (device_id, recorded_time, battery_level, signal_strength, uptime_pct, error_count, health_status)
SELECT device_id, recorded_time, battery_level, signal_strength, uptime_pct, error_count,
       CASE WHEN uptime_pct < 90 OR error_count >= 5 OR battery_level < 15 THEN 'CRITICAL'
            WHEN uptime_pct < 97 OR error_count >= 2 OR battery_level < 30 OR signal_strength < 40 THEN 'WARNING'
            ELSE 'HEALTHY' END
FROM (
    SELECT d.device_id,
           (CURRENT_DATE - g) + TIME '12:00' AS recorded_time,
           CASE WHEN d.device_type = 'MOBILE_READER' THEN (10 + random() * 90)::int END AS battery_level,
           CASE WHEN d.device_type = 'MOBILE_READER' THEN (30 + random() * 70)::int
                ELSE (60 + random() * 40)::int END AS signal_strength,
           ROUND((CASE WHEN d.status IN ('ERROR', 'MAINTENANCE') THEN 80 + random() * 15
                       ELSE 96 + random() * 4 END)::numeric, 2) AS uptime_pct,
           CASE WHEN d.status = 'ERROR' THEN (2 + random() * 6)::int
                ELSE (random() * random() * 4)::int END AS error_count
    FROM device.device d
    CROSS JOIN generate_series(0, 29) AS g
    WHERE d.status <> 'DECOMMISSIONED'
) m
ON CONFLICT (device_id, recorded_time) DO NOTHING;

-- Fees on approved and refunded transactions (rates by card type)
INSERT INTO txn.transaction_fee (transaction_id, customer_id, device_id, fee_type, fee_amount, fee_time)
SELECT pt.transaction_id, pt.customer_id, pt.device_id, f.fee_type, f.fee_amount, pt.transaction_time
FROM txn.payment_transaction pt
CROSS JOIN LATERAL (VALUES
    ('INTERCHANGE', ROUND(pt.amount * CASE pt.card_type
                                          WHEN 'VISA' THEN 0.0180 WHEN 'MASTERCARD' THEN 0.0185
                                          WHEN 'AMEX' THEN 0.0250 WHEN 'DISCOVER' THEN 0.0190
                                          WHEN 'DEBIT' THEN 0.0050 WHEN 'PREPAID' THEN 0.0120
                                          ELSE 0.0200 END, 2)),
    ('SCHEME',      ROUND(pt.amount * 0.0013, 2)),
    ('PROCESSING',  ROUND(pt.amount * 0.0025 + 0.10, 2)),
    ('GATEWAY',     CASE WHEN pt.entry_mode IN ('ONLINE', 'QR') THEN 0.05 END)
) AS f(fee_type, fee_amount)
WHERE pt.transaction_status IN ('APPROVED', 'REFUNDED')
  AND f.fee_amount IS NOT NULL
ON CONFLICT (transaction_id, fee_type) DO NOTHING;

-- Full refunds for REFUNDED transactions + partial refund requests on ~3% of approved ones
INSERT INTO txn.refund (transaction_id, customer_id, device_id, refund_amount, refund_reason, refund_status,
                        requested_time, processed_time)
SELECT transaction_id, customer_id, device_id, refund_amount, refund_reason, refund_status, requested_time,
       CASE WHEN refund_status <> 'REQUESTED'
            THEN LEAST(requested_time + ((transaction_id % 48) + 2) * INTERVAL '1 hour', LOCALTIMESTAMP) END
FROM (
    SELECT pt.transaction_id, pt.customer_id, pt.device_id,
           CASE WHEN pt.transaction_status = 'REFUNDED' THEN pt.amount
                ELSE GREATEST(ROUND(pt.amount * 0.5, 2), 0.01) END AS refund_amount,
           (ARRAY['CUSTOMER_REQUEST', 'DUPLICATE_CHARGE', 'PRODUCT_RETURN', 'SERVICE_ISSUE', 'FRAUD'])
               [pt.transaction_id % 5 + 1] AS refund_reason,
           CASE WHEN pt.transaction_status = 'REFUNDED' THEN 'COMPLETED'
                ELSE (ARRAY['REQUESTED', 'APPROVED', 'COMPLETED', 'REJECTED'])[pt.transaction_id % 4 + 1] END AS refund_status,
           LEAST(pt.transaction_time + ((pt.transaction_id % 72) + 1) * INTERVAL '1 hour', LOCALTIMESTAMP) AS requested_time
    FROM txn.payment_transaction pt
    WHERE pt.amount > 0
      AND (pt.transaction_status = 'REFUNDED'
           OR (pt.transaction_status = 'APPROVED' AND pt.transaction_id % 37 = 0))
) r
ON CONFLICT (transaction_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5. Privileges (app role reads metadata; datatalk_ro runs the reports)
-- ---------------------------------------------------------------------
GRANT SELECT ON device.device_health_metric, txn.transaction_fee, txn.refund TO user_datatalk, datatalk_ro;

-- ---------------------------------------------------------------------
-- 6. Verification
-- ---------------------------------------------------------------------
SELECT 'device_health_metric' AS table_name, COUNT(*) AS row_count FROM device.device_health_metric
UNION ALL SELECT 'transaction_fee', COUNT(*) FROM txn.transaction_fee
UNION ALL SELECT 'refund', COUNT(*) FROM txn.refund;
