CREATE TABLE drive_file (
                            drive_file_id  BIGSERIAL PRIMARY KEY,
                            original_name  VARCHAR(255) NOT NULL,
                            stored_name    VARCHAR(255) NOT NULL,
                            bucket         VARCHAR(100) NOT NULL,
                            size           BIGINT NOT NULL,
                            content_type   VARCHAR(100) NOT NULL,
                            uploaded_by    BIGINT NOT NULL REFERENCES member(member_id),
                            created_at     TIMESTAMP NOT NULL
);
