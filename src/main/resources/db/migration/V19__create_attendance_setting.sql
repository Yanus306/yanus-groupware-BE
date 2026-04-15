CREATE TABLE attendance_setting (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    auto_checkout_time  TIME NOT NULL DEFAULT '23:59:59'
);

INSERT INTO attendance_setting (auto_checkout_time) VALUES ('23:59:59');