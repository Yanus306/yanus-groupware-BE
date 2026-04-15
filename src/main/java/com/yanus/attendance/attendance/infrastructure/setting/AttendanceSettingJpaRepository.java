package com.yanus.attendance.attendance.infrastructure.setting;

import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import com.yanus.attendance.attendance.domain.setting.AttendanceSettingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AttendanceSettingJpaRepository implements AttendanceSettingRepository {

    private final AttendanceSettingSpringDataRepository repository;

    @Override
    public AttendanceSetting save(AttendanceSetting setting) {
        return repository.save(setting);
    }

    @Override
    public Optional<AttendanceSetting> find() {
        return repository.findAll().stream().findFirst();
    }
}
