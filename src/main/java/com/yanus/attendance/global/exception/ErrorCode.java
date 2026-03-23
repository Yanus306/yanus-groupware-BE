package com.yanus.attendance.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "중복된 요청입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "Refresh Token이 존재하지 않습니다."),

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "존재하지 않는 회원입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),

    // Attendance
    ALREADY_CHECKED_IN(HttpStatus.BAD_REQUEST, "ALREADY_CHECKED_IN", "이미 출근 처리되었습니다."),
    NOT_CHECKED_IN(HttpStatus.BAD_REQUEST, "NOT_CHECKED_IN", "오늘 출근 기록이 없습니다."),
    ALREADY_CHECKED_OUT(HttpStatus.BAD_REQUEST, "ALREADY_CHECKED_OUT", "이미 퇴근 처리되었습니다."),
    INVALID_CHECKOUT_TIME(HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_TIME", "퇴근 시간은 출근 시간 이후여야 합니다."),
    ATTENDANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "ATTENDANCE_NOT_FOUND", "출근 기록을 찾을 수 없습니다."),
    INVALID_WORK_SCHEDULE_TIME(HttpStatus.BAD_REQUEST, "INVALID_WORK_SCHEDULE_TIME", "종료 시간은 시작 시간 이후여야 합니다."),

    // Leave
    LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "LEAVE_REQUEST_NOT_FOUND", "휴가 신청을 찾을 수 없습니다."),
    ALREADY_REVIEWED(HttpStatus.BAD_REQUEST, "ALREADY_REVIEWED", "이미 처리된 휴가 신청입니다."),

    // Task
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "태스크를 찾을 수 없습니다."),

    // Calendar
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CALENDAR_EVENT_NOT_FOUND", "캘린더 이벤트를 찾을 수 없습니다."),
    INVALID_CALENDAR_END_TIME(HttpStatus.BAD_REQUEST, "INVALID_CALENDAR_END_TIME", "종료 일시는 시작 일시 이후여야 합니다."),

    // Drive
    DRIVE_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "DRIVE_FILE_NOT_FOUND", "파일을 찾을 수 없습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
