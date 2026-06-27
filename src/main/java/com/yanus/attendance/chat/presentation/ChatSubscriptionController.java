package com.yanus.attendance.chat.presentation;

import com.yanus.attendance.chat.infrastructure.ChatSseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "채팅 실시간", description = "SSE 구독 (온라인 사용자 실시간 메시지 수신)")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChatSubscriptionController {

    private final ChatSseService sseService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Long memberId) {
        return sseService.subscribe(memberId);
    }
}
