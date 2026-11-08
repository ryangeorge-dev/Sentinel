-- SQL CHECK accepts UNKNOWN as well as TRUE; explicit IS NOT NULL guards
-- prevent missing status codes from passing a nominally healthy observation.
ALTER TABLE check_results ADD CONSTRAINT valid_check_outcome CHECK (
    (status IN ('UP', 'DEGRADED') AND http_status IS NOT NULL
        AND http_status BETWEEN 200 AND 299 AND failure_reason IS NULL)
    OR (status = 'DOWN' AND failure_reason = 'HTTP_ERROR' AND http_status IS NOT NULL
        AND (http_status < 200 OR http_status >= 300))
    OR (status = 'DOWN' AND http_status IS NULL AND failure_reason IN (
        'TIMEOUT', 'DNS_FAILURE', 'CONNECTION_FAILURE', 'TLS_FAILURE', 'BLOCKED_DESTINATION', 'NETWORK_ERROR'
    ))
);
