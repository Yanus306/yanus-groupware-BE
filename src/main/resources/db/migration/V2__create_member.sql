CREATE TABLE member
(
    member_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    team_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    CONSTRAINT fk_member_team FOREIGN KEY (team_id) REFERENCES team (team_id)
);
