CREATE TABLE channel_mute
(
    channel_mute_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id       BIGINT    NOT NULL REFERENCES member (member_id),
    channel_id      BIGINT    NOT NULL REFERENCES channel (channel_id),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_channel_mute UNIQUE (member_id, channel_id)
);

CREATE INDEX idx_channel_mute_member ON channel_mute (member_id);
