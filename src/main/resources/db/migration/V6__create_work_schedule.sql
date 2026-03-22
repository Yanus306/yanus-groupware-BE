CREATE TABLE work_schedule (
                               work_schedule_id BIGSERIAL PRIMARY KEY,
                               member_id        BIGINT NOT NULL REFERENCES member(member_id),
                               day_of_week      VARCHAR(10) NOT NULL,
                               start_time       TIME NOT NULL,
                               end_time         TIME NOT NULL,
                               UNIQUE (member_id, day_of_week)
);
