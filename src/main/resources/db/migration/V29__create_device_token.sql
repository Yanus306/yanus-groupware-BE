CREATE TABLE device_token
(
    device_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       BIGINT       NOT NULL REFERENCES member (member_id),
    token           VARCHAR(512) NOT NULL UNIQUE,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_device_token_member ON device_token (member_id);
