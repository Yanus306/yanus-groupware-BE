CREATE TABLE audit_log (
    audit_log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id     BIGINT       NOT NULL,
    actor_role   VARCHAR(20)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    action       VARCHAR(30)  NOT NULL,
    previous_value VARCHAR(100),
    new_value    VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL
);
