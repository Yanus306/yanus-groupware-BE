ALTER TABLE work_schedule_event
    ADD COLUMN ends_next_day BOOLEAN NOT NULL DEFAULT FALSE;