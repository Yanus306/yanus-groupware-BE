CREATE TABLE attendance_exception (
                                      id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      member_id           BIGINT NOT NULL REFERENCES member(member_id),
                                      attendance_id       BIGINT REFERENCES attendance(attendance_id),
                                      work_date           DATE NOT NULL,
                                      type                VARCHAR(30) NOT NULL,
                                      status              VARCHAR(20) NOT NULL,
                                      note                TEXT,
                                      reason              TEXT,
                                      approved_by         VARCHAR(100),
                                      approved_at         TIMESTAMP,
                                      resolved_by         VARCHAR(100),
                                      resolved_at         TIMESTAMP,
                                      created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                                      CONSTRAINT uk_member_workdate_type UNIQUE (member_id, work_date, type)
);

CREATE INDEX idx_attendance_exception_workdate ON attendance_exception(work_date);
CREATE INDEX idx_attendance_exception_status ON attendance_exception(status);