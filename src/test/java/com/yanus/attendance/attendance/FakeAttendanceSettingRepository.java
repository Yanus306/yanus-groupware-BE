package com.yanus.attendance.attendance;

import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import com.yanus.attendance.attendance.domain.setting.AttendanceSettingRepository;

import java.util.Optional;

public class FakeAttendanceSettingRepository implements AttendanceSettingRepository {

    private AttendanceSetting store;

    @Override
    public AttendanceSetting save(AttendanceSetting setting) {
        this.store = setting;
        return setting;
    }

    @Override
    public Optional<AttendanceSetting> find() {
        return Optional.ofNullable(store);
    }
}