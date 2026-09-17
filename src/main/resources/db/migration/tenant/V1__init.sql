CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    role     VARCHAR(50)  NOT NULL
);

CREATE TABLE trades (
    id              BIGSERIAL    PRIMARY KEY,
    trade_id        VARCHAR(255) NOT NULL,
    source          VARCHAR(50)  NOT NULL,
    symbol          VARCHAR(50)  NOT NULL,
    price           NUMERIC(19,6) NOT NULL,
    quantity        NUMERIC(19,6) NOT NULL,
    trade_date      DATE          NOT NULL,
    settlement_date DATE          NOT NULL,
    side            VARCHAR(50)   NOT NULL,
    counterparty    VARCHAR(255)  NOT NULL,
    currency        CHAR(3)       NOT NULL,
    UNIQUE (trade_id, source)
);

CREATE TABLE trade_breaks (
    id                           BIGSERIAL    PRIMARY KEY,
    trade_id                     VARCHAR(255) NOT NULL,
    break_type                   VARCHAR(50)  NOT NULL,
    blotter_value                VARCHAR(255),
    custodian_value              VARCHAR(255),
    detected_at                  TIMESTAMP    NOT NULL,
    status                       VARCHAR(50)  NOT NULL,
    notional_impact              NUMERIC(19,6),
    materiality_tier             VARCHAR(50),
    bps_deviation                NUMERIC(19,6),
    settlement_date              TIMESTAMP,
    minutes_to_settlement        BIGINT,
    estimated_resolution_minutes BIGINT,
    composite_score              BIGINT,
    settlement_fail_risk         BOOLEAN   NOT NULL DEFAULT FALSE,
    assigned_to                  VARCHAR(255),
    last_change_at               TIMESTAMP,
    sla_breached                 BOOLEAN   NOT NULL DEFAULT FALSE,
    investigation_started_at     TIMESTAMP,
    resolved_at                  TIMESTAMP
);

CREATE TABLE trade_break_audit (
    id             BIGSERIAL    PRIMARY KEY,
    trade_break_id BIGINT       NOT NULL REFERENCES trade_breaks(id),
    from_status    VARCHAR(50),
    to_status      VARCHAR(50),
    changed_at     TIMESTAMP,
    changed_by     VARCHAR(255),
    duration       BIGINT,
    notes          TEXT
);

CREATE TABLE trade_match_candidates (
    id                 BIGSERIAL    PRIMARY KEY,
    trade_break_id     BIGINT       NOT NULL REFERENCES trade_breaks(id),
    candidate_trade_id VARCHAR(255) NOT NULL,
    confidence_score   BIGINT       NOT NULL,
    match_tier         VARCHAR(50)  NOT NULL,
    rank               INTEGER      NOT NULL,
    rejected           BOOLEAN      NOT NULL DEFAULT FALSE,
    rejected_at        TIMESTAMP,
    rejected_by        VARCHAR(255),
    score_breakdown    TEXT
);

CREATE TABLE export_jobs (
    id              VARCHAR(36)  PRIMARY KEY,
    model_type      VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    recipient_email VARCHAR(255) NOT NULL,
    tenant_schema   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    file_key        TEXT,
    error_message   TEXT
);

CREATE INDEX idx_trades_fuzzy_block        ON trades                  (source, symbol, settlement_date);
CREATE INDEX idx_trades_source             ON trades                  (source);
CREATE INDEX idx_breaks_dedup              ON trade_breaks            (trade_id, break_type, status);
CREATE INDEX idx_breaks_status             ON trade_breaks            (status);
CREATE INDEX idx_breaks_sla                ON trade_breaks            (status, settlement_date) WHERE status = 'OPEN';
CREATE INDEX idx_audit_break_id            ON trade_break_audit       (trade_break_id);
CREATE INDEX idx_candidates_break_active   ON trade_match_candidates  (trade_break_id, rejected, rank);
CREATE INDEX idx_candidates_break_score    ON trade_match_candidates  (trade_break_id, confidence_score DESC);
