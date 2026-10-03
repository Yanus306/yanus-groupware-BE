package com.yanus.attendance.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void integrationCauseIsPreserved() {
        var cause = new java.io.IOException("private cause");
        var failure = new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, cause);
        assertThat(failure.getCause()).isSameAs(cause);
        assertThat(failure.getMessage()).isEqualTo(ErrorCode.FILE_UPLOAD_FAILED.getMessage());
    }

    @Test
    @DisplayName("ErrorCode로 예외를 생성")
    void ErrorCode로_예외를_생성한다() {
        // given
        ErrorCode errorCode = ErrorCode.MEMBER_NOT_FOUND;

        // when
        BusinessException exception = new BusinessException(errorCode);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("RuntimeException을 상속")
    void RuntimeException을_상속한다() {
        // given
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;

        // when
        BusinessException exception = new BusinessException(errorCode);

        // then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
