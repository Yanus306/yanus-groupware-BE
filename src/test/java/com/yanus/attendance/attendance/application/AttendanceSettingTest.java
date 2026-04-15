package com.yanus.attendance.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.FakeAttendanceSettingRepository;
import com.yanus.attendance.attendance.domain.setting.AttendanceSetting;
import com.yanus.attendance.attendance.presentation.dto.AutoCheckoutTimeRequest;
import com.yanus.attendance.attendance.presentation.dto.AutoCheckoutTimeResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;

class AttendanceSettingServiceTest {

    private AttendanceSettingService attendanceSettingService;
    private FakeMemberRepository memberRepository;
    private FakeAttendanceSettingRepository settingRepository;

    @BeforeEach
    void setUp() {
        memberRepository = new FakeMemberRepository();
        settingRepository = new FakeAttendanceSettingRepository();
        attendanceSettingService = new AttendanceSettingService(settingRepository, memberRepository);
    }

    private Member createMember(MemberRole role) {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("테스터", "test@test.com", "password", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("관리자가 자동 체크아웃 시간을 조회하면 기본값 23:59:59 반환")
    void 관리자가_자동_체크아웃_시간_조회_시_기본값_반환() {
        // given
        Member admin = createMember(MemberRole.ADMIN);

        // when
        AutoCheckoutTimeResponse response = attendanceSettingService.getAutoCheckoutTime(admin.getId());

        // then`
        assertThat(response.autoCheckoutTime()).isEqualTo(LocalTime.of(23, 59, 59));
    }

    @Test
    @DisplayName("관리자가 자동 체크아웃 시간을 변경하면 저장됨")
    void 관리자가_자동_체크아웃_시간_변경_시_저장됨() {
        // given
        Member admin = createMember(MemberRole.ADMIN);
        AutoCheckoutTimeRequest request = new AutoCheckoutTimeRequest(LocalTime.of(22, 0, 0));

        // when
        AutoCheckoutTimeResponse response = attendanceSettingService.updateAutoCheckoutTime(admin.getId(), request);

        // then
        assertThat(response.autoCheckoutTime()).isEqualTo(LocalTime.of(22, 0, 0));
    }

    @Test
    @DisplayName("일반 멤버가 자동 체크아웃 시간을 변경하면 예외 발생")
    void 일반_멤버가_자동_체크아웃_시간_변경_시_예외_발생() {
        // given
        Member member = createMember(MemberRole.MEMBER);
        AutoCheckoutTimeRequest request = new AutoCheckoutTimeRequest(LocalTime.of(22, 0, 0));

        // when & then
        assertThatThrownBy(() -> attendanceSettingService.updateAutoCheckoutTime(member.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("변경 후 조회하면 변경된 시간 반환")
    void 변경_후_조회하면_변경된_시간_반환() {
        // given
        Member admin = createMember(MemberRole.ADMIN);
        attendanceSettingService.updateAutoCheckoutTime(admin.getId(),
                new AutoCheckoutTimeRequest(LocalTime.of(21, 0, 0)));

        // when
        AutoCheckoutTimeResponse response = attendanceSettingService.getAutoCheckoutTime(admin.getId());

        // then
        assertThat(response.autoCheckoutTime()).isEqualTo(LocalTime.of(21, 0, 0));
    }
}