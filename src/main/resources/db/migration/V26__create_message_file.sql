CREATE TABLE message_file
(
    message_file_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id      BIGINT       NOT NULL REFERENCES message (message_id),
    original_name   VARCHAR(255) NOT NULL,
    stored_name     VARCHAR(255) NOT NULL,
    bucket          VARCHAR(100) NOT NULL,
    size            BIGINT       NOT NULL,
    content_type    VARCHAR(100),
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_message_file_message ON message_file (message_id);
