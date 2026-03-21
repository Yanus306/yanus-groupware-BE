CREATE TABLE attendance
(
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT      NOT NULL REFERENCES member (id),
    work_date      DATE        NOT NULL,
    check_in_time  TIMESTAMP   NOT NULL,
    check_out_time TIMESTAMP,
    status         VARCHAR(20) NOT NULL,
    UNIQUE (member_id, work_date)
);
