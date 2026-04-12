CREATE TABLE email_verification_token (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(64) NOT NULL UNIQUE,
    member_id   BIGINT      NOT NULL REFERENCES member(member_id),
    expires_at  TIMESTAMP   NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);