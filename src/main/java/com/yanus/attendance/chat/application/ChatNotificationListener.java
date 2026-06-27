package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.application.event.NewMessageEvent;
import com.yanus.attendance.chat.domain.ChannelMemberRepository;
import com.yanus.attendance.chat.domain.ChannelMuteRepository;
import com.yanus.attendance.chat.domain.DeviceTokenRepository;
import com.yanus.attendance.chat.domain.MessageType;
import com.yanus.attendance.chat.infrastructure.ChatSseService;
import com.yanus.attendance.chat.presentation.dto.MessageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 메시지를 채널 멤버에게 전달한다.
 * - 온라인(SSE 연결 보유) 멤버: SSE 푸시
 * - 오프라인 + 알림 미음소거 멤버: FCM 푸시
 */
@Component
@RequiredArgsConstructor
public class ChatNotificationListener {

    private final ChatSseService sseService;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMuteRepository channelMuteRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmSender fcmSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewMessage(NewMessageEvent event) {
        MessageResponse message = event.message();
        Long channelId = message.channelId();
        List<Long> memberIds = channelMemberRepository.findMemberIdsByChannelId(channelId);

        List<String> offlineTokens = new ArrayList<>();
        for (Long memberId : memberIds) {
            if (memberId.equals(event.senderId())) {
                continue;
            }
            if (sseService.isOnline(memberId)) {
                sseService.sendToMember(memberId, "message", message);
                continue;
            }
            // 오프라인: 음소거하지 않은 멤버에게만 FCM 발송 대상으로 수집
            if (!channelMuteRepository.existsByMemberIdAndChannelId(memberId, channelId)) {
                offlineTokens.addAll(deviceTokenRepository.findTokensByMemberId(memberId));
            }
        }

        if (!offlineTokens.isEmpty()) {
            fcmSender.sendToTokens(offlineTokens, message.senderName(), buildPreview(message),
                    Map.of("channelId", String.valueOf(channelId), "messageId", String.valueOf(message.id())));
        }
    }

    private String buildPreview(MessageResponse message) {
        if (message.type() == MessageType.FILE) {
            return "파일을 보냈습니다.";
        }
        return message.content() == null ? "" : message.content();
    }
}
