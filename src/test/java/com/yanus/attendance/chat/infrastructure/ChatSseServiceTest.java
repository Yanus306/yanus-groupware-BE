package com.yanus.attendance.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class ChatSseServiceTest {

    private ChatSseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new ChatSseService();
    }

    @Test
    @DisplayName("구독하면 온라인 상태가 된다")
    void subscribe_marks_online() {
        // given
        assertThat(sseService.isOnline(1L)).isFalse();

        // when
        SseEmitter emitter = sseService.subscribe(1L);

        // then
        assertThat(emitter).isNotNull();
        assertThat(sseService.isOnline(1L)).isTrue();
    }

    @Test
    @DisplayName("구독하지 않은 멤버는 오프라인이다")
    void not_subscribed_is_offline() {
        assertThat(sseService.isOnline(999L)).isFalse();
    }

    @Test
    @DisplayName("오프라인 멤버에게 전송해도 예외가 발생하지 않는다")
    void send_to_offline_is_noop() {
        assertThatCode(() -> sseService.sendToMember(999L, "message", "hi"))
                .doesNotThrowAnyException();
    }
}
