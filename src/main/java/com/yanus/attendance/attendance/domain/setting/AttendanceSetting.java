package com.yanus.attendance.attendance.domain.setting;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class AttendanceSetting {

    private static final LocalTime DEFAULT_AUTO_CHECKOUT_TIME = LocalTime.of(23, 59, 59);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalTime autoCheckoutTime;

    public static AttendanceSetting createDefault() {
        AttendanceSetting setting = new AttendanceSetting();
        setting.autoCheckoutTime = DEFAULT_AUTO_CHECKOUT_TIME;
        return setting;
    }

    public void updateAutoCheckoutTime(LocalTime time) {
        this.autoCheckoutTime = time;
    }
}