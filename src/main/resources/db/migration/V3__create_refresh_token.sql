CREATE TABLE refresh_token
(
    refresh_token_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token            VARCHAR(512) NOT NULL UNIQUE,
    member_id        BIGINT       NOT NULL,
    expires_at       TIMESTAMP    NOT NULL,
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);
