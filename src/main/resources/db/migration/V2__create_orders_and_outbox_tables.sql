CREATE TABLE IF NOT EXISTS orders (
    id         BIGSERIAL    NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id              BIGSERIAL     NOT NULL,
    event_id        UUID          NOT NULL,
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    VARCHAR(100)  NOT NULL,
    event_type      VARCHAR(150)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    retry_count     INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    published_at    TIMESTAMPTZ,
    locked_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id),
    CONSTRAINT chk_outbox_event_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RETRY', 'PUBLISHED', 'DEAD'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_dispatch
    ON outbox_event (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_stuck
    ON outbox_event (status, locked_at);
