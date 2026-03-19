package com.yanus.attendance.global.response;

public record ApiResponse<T>(
        String code,
        String message,
        T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "요청이 성공했습니다.", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>("CREATED", "생성되었습니다.", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>("SUCCESS", "요청이 성공했습니다.", null);
    }
}
