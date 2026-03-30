package com.yanus.attendance.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AttendanceSettlementCalculatorTest {

    @Test
    @DisplayName("정시 출근시 요금 0원")
    void regular_check_in_is_free() {
        // given
        LocalTime scheduled = LocalTime.of(9, 0);
        LocalDateTime checkIn = LocalDateTime.of(2026, 3, 4, 9, 0, 0);

        // when
        int lateMinutes = AttendanceSettlementCalculator.calculateLateMinutes(scheduled, checkIn);

        // then
        assertThat(lateMinutes).isZero();
    }

    @Test
    void fifteen_nine_minutes_after_scheduled_time_is_late() {
        // given
        LocalTime scheduled = LocalTime.of(9, 0);
        LocalDateTime checkIn = LocalDateTime.of(2026, 3, 4, 9, 7, 59);

        // when
        int lateMinutes = AttendanceSettlementCalculator.calculateLateMinutes(scheduled, checkIn);

        // then
        assertThat(lateMinutes).isEqualTo(7);
    }

    @Test
    @DisplayName("지각비는 분당 백원")
    void late_fee_is_per_minute() {
        // when
        int fee = AttendanceSettlementCalculator.calculateFee(27);

        // then
        assertThat(fee).isEqualTo(2700);
    }

    @Test
    @DisplayName("조기 출근은 지각비 0원")
    void early_check_in_is_free() {
        // given
        LocalTime scheduled = LocalTime.of(9, 0);
        LocalDateTime checkIn = LocalDateTime.of(2026, 3, 4, 8, 50, 0);

        // when
        int lateMinutes = AttendanceSettlementCalculator.calculateLateMinutes(scheduled, checkIn);

        // then
        assertThat(lateMinutes).isZero();
    }
}
