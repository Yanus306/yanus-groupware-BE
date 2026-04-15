package com.yanus.attendance.attendance.infrastructure.setting;

import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSettingSpringDataRepository extends JpaRepository<AttendanceSetting, Long> {
}
