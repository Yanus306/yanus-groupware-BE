CREATE TABLE work_schedule_event (
    work_schedule_event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id   BIGINT    NOT NULL,
    date        DATE      NOT NULL,
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    FOREIGN KEY (member_id) REFERENCES member(member_id),
    UNIQUE (member_id, date)
);
