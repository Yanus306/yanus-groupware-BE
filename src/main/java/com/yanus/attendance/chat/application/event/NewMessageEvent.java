package com.yanus.attendance.chat.application.event;

import com.yanus.attendance.chat.presentation.dto.MessageResponse;

/**
 * 새 메시지가 저장된 뒤 발행되는 도메인 이벤트.
 * 트랜잭션 커밋 이후 SSE/FCM 알림 라우팅에 사용된다.
 */
public record NewMessageEvent(MessageResponse message, Long senderId) {
}
