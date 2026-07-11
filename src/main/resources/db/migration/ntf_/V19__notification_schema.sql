-- ===========================================================================
-- SAANJHA 2.0: V19 MIGRATION (NOTIFICATION MODULE)
-- Schema: ntf. Every table here is owned exclusively by the notification
-- module - no other module's Flyway migration or JPA repository touches
-- this schema (Boundary Rule).
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS ntf;

-- ---------------------------------------------------------------------------
-- ntf_notifications: the aggregate root. One row per (recipient, source
-- domain event) - see Notification.java's javadoc for why idempotency is
-- enforced at the constraint level rather than only in application code.
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_notifications (
    id                UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    event_type        VARCHAR(64) NOT NULL,
    category          VARCHAR(20) NOT NULL,
    priority          VARCHAR(10) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT,
    action_url        VARCHAR(500),
    source_event_id   VARCHAR(150) NOT NULL,
    payload           JSONB NOT NULL DEFAULT '{}',
    status            VARCHAR(20) NOT NULL,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    CONSTRAINT uq_ntf_recipient_source_event UNIQUE (recipient_user_id, source_event_id)
);

CREATE INDEX idx_ntf_notifications_recipient_feed ON ntf.ntf_notifications (recipient_user_id, created_at DESC);
CREATE INDEX idx_ntf_notifications_recipient_unread ON ntf.ntf_notifications (recipient_user_id, read_at) WHERE read_at IS NULL;

-- ---------------------------------------------------------------------------
-- ntf_deliveries: one row per (notification, channel). This IS the dispatch
-- outbox - see NotificationDelivery.java's javadoc.
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_deliveries (
    id                 UUID PRIMARY KEY,
    notification_id    UUID NOT NULL REFERENCES ntf.ntf_notifications (id),
    channel            VARCHAR(15) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    mode               VARCHAR(10) NOT NULL DEFAULT 'INSTANT',
    last_provider      VARCHAR(20),
    attempt_count      INT NOT NULL DEFAULT 0,
    max_attempts       INT NOT NULL,
    last_error         VARCHAR(1000),
    next_attempt_at    TIMESTAMPTZ NOT NULL,
    sent_at            TIMESTAMPTZ,
    delivered_at       TIMESTAMPTZ,
    read_at            TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ NOT NULL,
    recipient_address  VARCHAR(320),
    version            BIGINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The dispatch scanner's one query (NotificationDeliveryRepository.findDueForDispatch)
-- is a direct hit on this index.
CREATE INDEX idx_ntf_deliveries_dispatch_scan ON ntf.ntf_deliveries (status, next_attempt_at);
CREATE INDEX idx_ntf_deliveries_notification ON ntf.ntf_deliveries (notification_id);

-- ---------------------------------------------------------------------------
-- ntf_provider_attempts: append-only audit trail, one row per actual
-- provider call. Never updated after insert.
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_provider_attempts (
    id             UUID PRIMARY KEY,
    delivery_id    UUID NOT NULL REFERENCES ntf.ntf_deliveries (id),
    provider       VARCHAR(20) NOT NULL,
    attempt_number INT NOT NULL,
    success        BOOLEAN NOT NULL,
    status_code    INT,
    error_message  VARCHAR(1000),
    latency_ms     BIGINT,
    attempted_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ntf_attempts_delivery ON ntf.ntf_provider_attempts (delivery_id);

-- ---------------------------------------------------------------------------
-- ntf_templates: versioned per (event_type, channel, locale).
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_templates (
    id                   UUID PRIMARY KEY,
    event_type           VARCHAR(64) NOT NULL,
    channel              VARCHAR(15) NOT NULL,
    locale               VARCHAR(10) NOT NULL DEFAULT 'en',
    subject_template     VARCHAR(500),
    body_template         TEXT NOT NULL,
    action_url_template  VARCHAR(500),
    version              INT NOT NULL DEFAULT 1,
    is_active            BOOLEAN NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ntf_templates_lookup ON ntf.ntf_templates (event_type, channel, locale, is_active);

-- ---------------------------------------------------------------------------
-- ntf_preferences: global, per-user settings. Row is created lazily on first
-- read/write, not seeded per-user at signup (see entity javadoc).
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_preferences (
    user_id            UUID PRIMARY KEY,
    email_enabled      BOOLEAN NOT NULL DEFAULT true,
    sms_enabled        BOOLEAN NOT NULL DEFAULT false,
    push_enabled       BOOLEAN NOT NULL DEFAULT true,
    in_app_enabled     BOOLEAN NOT NULL DEFAULT true,
    webhook_enabled    BOOLEAN NOT NULL DEFAULT false,
    webhook_url        VARCHAR(500),
    do_not_disturb     BOOLEAN NOT NULL DEFAULT false,
    quiet_hours_start  TIME,
    quiet_hours_end    TIME,
    timezone           VARCHAR(50) NOT NULL DEFAULT 'UTC',
    locale             VARCHAR(10) NOT NULL DEFAULT 'en',
    default_mode       VARCHAR(10) NOT NULL DEFAULT 'INSTANT',
    channel_priority   JSONB NOT NULL DEFAULT '[]',
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- ntf_event_preferences: per-(user, eventType) override tier.
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_event_preferences (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT true,
    mode       VARCHAR(10),
    CONSTRAINT uq_ntf_event_pref_user_event UNIQUE (user_id, event_type)
);

-- ---------------------------------------------------------------------------
-- ntf_provider_health: persisted mirror of Resilience4j's circuit state, for
-- cross-restart visibility and provider-ordering bias (see ProviderHealth.java).
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_provider_health (
    provider_channel_key  VARCHAR(40) PRIMARY KEY,
    provider              VARCHAR(20) NOT NULL,
    channel               VARCHAR(15) NOT NULL,
    consecutive_failures  INT NOT NULL DEFAULT 0,
    total_attempts        BIGINT NOT NULL DEFAULT 0,
    total_failures        BIGINT NOT NULL DEFAULT 0,
    last_success_at       TIMESTAMPTZ,
    last_failure_at       TIMESTAMPTZ,
    last_error            VARCHAR(500),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- ntf_dead_letters: terminal home for exhausted deliveries (module brief's
-- "Dead Letter Queue abstraction"). Append-only - requeue creates a new
-- ntf_deliveries row rather than mutating one of these (see entity javadoc).
-- ---------------------------------------------------------------------------
CREATE TABLE ntf.ntf_dead_letters (
    id                UUID PRIMARY KEY,
    delivery_id       UUID NOT NULL REFERENCES ntf.ntf_deliveries (id),
    notification_id   UUID NOT NULL REFERENCES ntf.ntf_notifications (id),
    channel           VARCHAR(15) NOT NULL,
    reason            VARCHAR(1000) NOT NULL,
    payload_snapshot  JSONB NOT NULL DEFAULT '{}',
    moved_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ,
    resolved_by       UUID,
    resolution_note   VARCHAR(500)
);

CREATE INDEX idx_ntf_dlq_unresolved ON ntf.ntf_dead_letters (resolved_at);
