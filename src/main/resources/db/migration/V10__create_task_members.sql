CREATE TABLE task_members (
                              task_id   BIGINT NOT NULL REFERENCES task(task_id),
                              member_id BIGINT NOT NULL REFERENCES member(member_id),
                              PRIMARY KEY (task_id, member_id)
);
