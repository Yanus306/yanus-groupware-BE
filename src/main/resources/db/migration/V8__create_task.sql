CREATE TABLE task (
                      task_id      BIGSERIAL PRIMARY KEY,
                      title        VARCHAR(255) NOT NULL,
                      date         DATE NOT NULL,
                      time         TIME,
                      priority     VARCHAR(10) NOT NULL,
                      done         BOOLEAN NOT NULL DEFAULT FALSE,
                      is_team_task BOOLEAN NOT NULL DEFAULT FALSE,
                      team_id      BIGINT REFERENCES team(team_id),
                      assignee_id  BIGINT REFERENCES member(member_id),
                      created_by   BIGINT NOT NULL REFERENCES member(member_id),
                      created_at   TIMESTAMP NOT NULL,
                      updated_at   TIMESTAMP NOT NULL
);
