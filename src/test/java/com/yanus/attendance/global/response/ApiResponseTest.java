package com.yanus.attendance.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ApiResponseTest {

    @Test
    @DisplayName("success 데이터와 함께 성공 응답")
    void success_with_data() {
        //given
        String data = "hello";

        //when
        ApiResponse<String> response = ApiResponse.success(data);

        //then
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("요청이 성공했습니다.");
        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("success 데이터 없이 성공 응답")
    void success_without_data() {
        //given & when
        ApiResponse<Void> response = ApiResponse.success();

        //then
        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("created 생성 응답을 반환")
    void created_response() {
        //given
        Long id = 1L;

        //when
        ApiResponse<Long> response = ApiResponse.created(id);

        //then
        assertThat(response.code()).isEqualTo("CREATED");
        assertThat(response.data()).isEqualTo(1L);
    }
}
