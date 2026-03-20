CREATE TABLE team
(
    team_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL
);

INSERT INTO team (name, created_at)
VALUES ('BACKEND', NOW()),
       ('FRONTEND', NOW()),
       ('AI', NOW()),
       ('SECURITY', NOW());
