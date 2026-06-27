CREATE TABLE channel_member
(
    channel_member_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    channel_id        BIGINT    NOT NULL REFERENCES channel (channel_id),
    member_id         BIGINT    NOT NULL REFERENCES member (member_id),
    joined_at         TIMESTAMP NOT NULL,
    CONSTRAINT uq_channel_member UNIQUE (channel_id, member_id)
);

CREATE INDEX idx_channel_member_member ON channel_member (member_id);
