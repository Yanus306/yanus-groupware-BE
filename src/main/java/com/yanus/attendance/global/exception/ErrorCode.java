package com.yanus.attendance.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),
    NOT_FOUND(404, "NOT_FOUND", "리소스를 찾을 수 없습니다."),
    CONFLICT(409, "CONFLICT", "중복된 요청입니다."),

    // Auth
    INVALID_TOKEN(401, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(401, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(401, "REFRESH_TOKEN_NOT_FOUND", "Refresh Token이 존재하지 않습니다."),

    // Member
    MEMBER_NOT_FOUND(404, "MEMBER_NOT_FOUND", "존재하지 않는 회원입니다."),
    EMAIL_ALREADY_EXISTS(409, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),

    // Attendance
    ALREADY_CLOCKED_IN(409, "ALREADY_CLOCKED_IN", "이미 출근 처리되었습니다."),
    NOT_CLOCKED_IN(400, "NOT_CLOCKED_IN", "출근 상태가 아닙니다."),

    // Leave
    LEAVE_REQUEST_NOT_FOUND(404, "LEAVE_REQUEST_NOT_FOUND", "존재하지 않는 휴가 신청입니다."),

    INTERNAL_ERROR(500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
