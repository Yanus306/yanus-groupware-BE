CREATE TABLE email_verification_job (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_id         BIGINT UNIQUE REFERENCES email_verification_token (id) ON DELETE SET NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_id         UUID,
    lease_until      TIMESTAMPTZ,
    last_error_code  VARCHAR(32),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'CANCELLED')),
    CHECK ((status = 'PROCESSING') = (lease_id IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK ((lease_id IS NULL) = (lease_until IS NULL))
);

CREATE INDEX email_verification_job_due_idx
    ON email_verification_job (next_attempt_at, id) WHERE status = 'PENDING';

CREATE INDEX email_verification_job_expired_lease_idx
    ON email_verification_job (lease_until, id) WHERE status = 'PROCESSING';
