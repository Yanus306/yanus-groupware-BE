package com.yanus.attendance.attendance.domain.setting;

import java.util.Optional;

public interface AttendanceSettingRepository {
    AttendanceSetting save(AttendanceSetting setting);
    Optional<AttendanceSetting> find();
}
