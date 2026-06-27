package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.application.event.NewMessageEvent;
import com.yanus.attendance.chat.domain.ChannelMemberRepository;
import com.yanus.attendance.chat.infrastructure.ChatSseService;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 메시지를 채널 멤버에게 전달한다.
 * - 온라인(SSE 연결 보유) 멤버: SSE 푸시
 * - (오프라인 멤버 FCM 전송은 후속 작업)
 */
@Component
@RequiredArgsConstructor
public class ChatNotificationListener {

    private final ChatSseService sseService;
    private final ChannelMemberRepository channelMemberRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewMessage(NewMessageEvent event) {
        MessageResponse message = event.message();
        List<Long> memberIds = channelMemberRepository.findMemberIdsByChannelId(message.channelId());
        for (Long memberId : memberIds) {
            if (memberId.equals(event.senderId())) {
                continue;
            }
            if (sseService.isOnline(memberId)) {
                sseService.sendToMember(memberId, "message", message);
            }
        }
    }
}
