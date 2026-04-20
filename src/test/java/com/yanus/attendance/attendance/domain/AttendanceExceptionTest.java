package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.attendance.domain.exception.AttendanceException;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionStatus;
import com.yanus.attendance.attendance.domain.exception.AttendanceExceptionType;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AttendanceExceptionTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 4, 20);
    private static final LocalDateTime ACTED_AT = LocalDateTime.of(2026, 4, 20, 10, 0);

    private final Member member = Member.create(
            "홍길동", "hong@test.com", "pw",
            MemberRole.MEMBER, MemberStatus.ACTIVE, null);

    @Test
    @DisplayName("OPEN 상태에서 approve하면 승인 처리")
    void OPEN_status_approve_to_APPROVED_status() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.LATE);

        // when
        exception.approve("admin", ACTED_AT, "승인 처리");

        // then
        assertThat(exception.getStatus()).isEqualTo(AttendanceExceptionStatus.APPROVED);
        assertThat(exception.getApprovedBy()).isEqualTo("admin");
        assertThat(exception.getApprovedAt()).isEqualTo(ACTED_AT);
        assertThat(exception.getNote()).isEqualTo("승인 처리");
    }

    @Test
    @DisplayName("OPEN 상태에서 reject 하면 REJECTED 처리")
    void OPEN_status_reject_to_REJECTED_status() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.LATE);

        // when
        exception.reject("반려 사유");

        // then
        assertThat(exception.getStatus()).isEqualTo(AttendanceExceptionStatus.REJECTED);
        assertThat(exception.getNote()).isEqualTo("반려 사유");
    }

    @Test
    @DisplayName("OPEN 상태에서 resolve 하면 RESOLVED 처리")
    void OPEN_status_resolve_to_RESOLVED_status() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.MISSED_CHECK_OUT);

        // when
        exception.resolve("admin", ACTED_AT, "처리 완료");

        // then
        assertThat(exception.getStatus()).isEqualTo(AttendanceExceptionStatus.RESOLVED);
        assertThat(exception.getResolvedBy()).isEqualTo("admin");
        assertThat(exception.getResolvedAt()).isEqualTo(ACTED_AT);
    }

    @Test
    @DisplayName("APPROVED 상태에서 resolve 하면 RESOLVED 처리")
    void APPROVED_status_resolve_to_RESOLVED_status() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.LATE);
        exception.approve("admin", ACTED_AT, null);

        // when
        exception.resolve("admin", ACTED_AT.plusHours(1), null);

        // then
        assertThat(exception.getStatus()).isEqualTo(AttendanceExceptionStatus.RESOLVED);
    }

    @Test
    @DisplayName("RESOLVED 상태에서 resolve 시 BusinessException 발생")
    void RESOLVED_status_reopen_throw_error() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.LATE);
        exception.resolve("admin", ACTED_AT, null);

        // when & then
        assertThatThrownBy(() -> exception.approve("admin", ACTED_AT, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("updateNote 로 note와 reason 을 수정한다")
    void updateNote_update_note_and_reason() {
        // given
        AttendanceException exception = AttendanceException.open(
                member, null, WORK_DATE, AttendanceExceptionType.LATE);

        // when
        exception.updateNote("새 메모", "지각 사유");

        // then
        assertThat(exception.getNote()).isEqualTo("새 메모");
        assertThat(exception.getReason()).isEqualTo("지각 사유");
    }
}
