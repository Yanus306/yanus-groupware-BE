CREATE TABLE calendar_event (
                                calendar_event_id BIGSERIAL PRIMARY KEY,
                                title             VARCHAR(255) NOT NULL,
                                start_date        DATE NOT NULL,
                                start_time        TIME NOT NULL,
                                end_date          DATE NOT NULL,
                                end_time          TIME NOT NULL,
                                created_by        BIGINT NOT NULL REFERENCES member(member_id),
                                created_at        TIMESTAMP NOT NULL,
                                updated_at        TIMESTAMP NOT NULL
);
