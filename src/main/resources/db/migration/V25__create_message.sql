CREATE TABLE message
(
    message_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_id BIGINT      NOT NULL REFERENCES channel (channel_id),
    sender_id  BIGINT      NOT NULL REFERENCES member (member_id),
    content    TEXT,
    type       VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL
);

CREATE INDEX idx_message_channel_created_at ON message (channel_id, created_at DESC);
