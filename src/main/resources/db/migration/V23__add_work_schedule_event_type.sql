ALTER TABLE work_schedule_event
    ADD COLUMN event_type VARCHAR(20) NOT NULL DEFAULT 'WORKING';

ALTER TABLE work_schedule_event
    ALTER COLUMN start_time DROP NOT NULL;

ALTER TABLE work_schedule_event
    ALTER COLUMN end_time DROP NOT NULL;

ALTER TABLE work_schedule_event
    ADD COLUMN reason VARCHAR(255);
