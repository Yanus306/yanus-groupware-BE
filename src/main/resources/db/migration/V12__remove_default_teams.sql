DELETE FROM team
WHERE team_id NOT IN (
    SELECT DISTINCT team_id FROM member WHERE team_id IS NOT NULL
);
