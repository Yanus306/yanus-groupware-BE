CREATE TABLE leave_request (
                               leave_request_id BIGSERIAL PRIMARY KEY,
                               member_id        BIGINT NOT NULL REFERENCES member(member_id),
                               category         VARCHAR(20) NOT NULL,
                               detail           TEXT,
                               date             DATE NOT NULL,
                               status           VARCHAR(20) NOT NULL,
                               submitted_at     TIMESTAMP NOT NULL,
                               reviewed_by      BIGINT REFERENCES member(member_id),
                               reviewed_at      TIMESTAMP
);
