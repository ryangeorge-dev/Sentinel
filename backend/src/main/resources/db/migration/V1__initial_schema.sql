CREATE TABLE monitors (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL CHECK (length(trim(name)) > 0),
    url varchar(2048) NOT NULL,
    interval_seconds integer NOT NULL CHECK (interval_seconds BETWEEN 5 AND 86400),
    timeout_ms integer NOT NULL CHECK (timeout_ms BETWEEN 100 AND 30000),
    degradation_threshold_ms integer NOT NULL CHECK (degradation_threshold_ms BETWEEN 1 AND 30000),
    enabled boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    config_revision bigint NOT NULL DEFAULT 0,
    next_check_at timestamptz NOT NULL,
    consecutive_failures integer NOT NULL DEFAULT 0 CHECK (consecutive_failures BETWEEN 0 AND 3),
    first_failure_at timestamptz,
    latest_status varchar(16) CHECK (latest_status IN ('UP', 'DEGRADED', 'DOWN')),
    last_checked_at timestamptz,
    latest_duration_ms bigint CHECK (latest_duration_ms >= 0),
    latest_http_status integer CHECK (latest_http_status BETWEEN 100 AND 599),
    CHECK (degradation_threshold_ms <= timeout_ms),
    CHECK ((consecutive_failures = 0) = (first_failure_at IS NULL))
);
CREATE INDEX monitors_due_idx ON monitors (next_check_at, id) WHERE enabled;
CREATE TABLE check_results (
    id uuid PRIMARY KEY,
    monitor_id uuid NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    checked_at timestamptz NOT NULL,
    http_status integer CHECK (http_status BETWEEN 100 AND 599),
    duration_ms bigint NOT NULL CHECK (duration_ms >= 0),
    status varchar(16) NOT NULL CHECK (status IN ('UP', 'DEGRADED', 'DOWN')),
    failure_reason varchar(40),
    CHECK ((status = 'DOWN') = (failure_reason IS NOT NULL)),
    CHECK (status = 'DOWN' OR http_status BETWEEN 200 AND 299)
);
CREATE INDEX checks_monitor_time_idx ON check_results (monitor_id, checked_at DESC, id DESC);
CREATE INDEX checks_time_idx ON check_results (checked_at DESC, id DESC);
CREATE TABLE incidents (
    id uuid PRIMARY KEY,
    monitor_id uuid NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    started_at timestamptz NOT NULL,
    recovered_at timestamptz,
    CHECK (recovered_at IS NULL OR recovered_at >= started_at)
);
CREATE UNIQUE INDEX one_open_incident_per_monitor ON incidents (monitor_id) WHERE recovered_at IS NULL;
CREATE INDEX incidents_monitor_time_idx ON incidents (monitor_id, started_at DESC, id DESC);
CREATE INDEX incidents_time_idx ON incidents (started_at DESC, id DESC);
