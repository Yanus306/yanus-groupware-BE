CREATE TABLE IF NOT EXISTS work_schedule_cache_revision (
    singleton BOOLEAN PRIMARY KEY CHECK (singleton),
    revision UUID NOT NULL DEFAULT gen_random_uuid()
);

INSERT INTO work_schedule_cache_revision (singleton) VALUES (TRUE)
ON CONFLICT (singleton) DO NOTHING;

CREATE OR REPLACE FUNCTION advance_work_schedule_cache_revision()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE work_schedule_cache_revision SET revision = gen_random_uuid() WHERE singleton;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS work_schedule_cache_revision_changed ON work_schedule;
CREATE TRIGGER work_schedule_cache_revision_changed
AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON work_schedule
FOR EACH STATEMENT EXECUTE FUNCTION advance_work_schedule_cache_revision();

DROP TRIGGER IF EXISTS work_schedule_cache_revision_member_changed ON member;
CREATE TRIGGER work_schedule_cache_revision_member_changed
AFTER UPDATE ON member
FOR EACH ROW
WHEN (OLD.name IS DISTINCT FROM NEW.name OR OLD.team_id IS DISTINCT FROM NEW.team_id)
EXECUTE FUNCTION advance_work_schedule_cache_revision();

DROP TRIGGER IF EXISTS work_schedule_cache_revision_team_changed ON team;
CREATE TRIGGER work_schedule_cache_revision_team_changed
AFTER UPDATE ON team
FOR EACH ROW
WHEN (OLD.name IS DISTINCT FROM NEW.name)
EXECUTE FUNCTION advance_work_schedule_cache_revision();
