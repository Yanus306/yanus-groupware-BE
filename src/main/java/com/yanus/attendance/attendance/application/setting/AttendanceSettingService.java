package com.yanus.attendance.attendance.application.setting;

import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import com.yanus.attendance.attendance.domain.setting.AttendanceSettingRepository;
import com.yanus.attendance.attendance.presentation.dto.AutoCheckoutTimeRequest;
import com.yanus.attendance.attendance.presentation.dto.AutoCheckoutTimeResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceSettingService {

    private final AttendanceSettingRepository settingRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public AutoCheckoutTimeResponse getAutoCheckoutTime() {
        return AutoCheckoutTimeResponse.from(getSetting());
    }

    public AutoCheckoutTimeResponse updateAutoCheckoutTime(Long actorId, AutoCheckoutTimeRequest request) {
        validateAdmin(actorId);
        AttendanceSetting setting = getSetting();
        setting.updateAutoCheckoutTime(request.autoCheckoutTime());
        return AutoCheckoutTimeResponse.from(settingRepository.save(setting));
    }

    public LocalTime getAutoCheckoutTimeValue() {
        return getSetting().getAutoCheckoutTime();
    }

    private AttendanceSetting getSetting() {
        return settingRepository.find()
                .orElse(AttendanceSetting.createDefault());
    }

    private AttendanceSetting getOrCreateDefault() {
        return settingRepository.find()
                .orElseGet(() -> settingRepository.save(AttendanceSetting.createDefault()));
    }

    private void validateAdmin(Long actorId) {
        Member actor = memberRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (actor.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
