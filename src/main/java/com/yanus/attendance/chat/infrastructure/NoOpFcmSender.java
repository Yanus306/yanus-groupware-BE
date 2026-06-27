package com.yanus.attendance.chat.infrastructure;

import com.yanus.attendance.chat.application.FcmSender;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Firebase 연동이 구성되지 않았을 때 사용하는 기본 FCM 발송 구현.
 * 실제 발송 대신 로그만 남긴다. (Firebase Admin SDK 구현 빈이 등록되면 그쪽이 우선)
 * 실제 연동 방법은 docs/chat-fcm-setup.md 참고.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(name = "firebaseFcmSender")
public class NoOpFcmSender implements FcmSender {

    @Override
    public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        log.info("[FCM-NOOP] 푸시 발송 대상 {}건 (title='{}'). Firebase 미구성 상태라 실제 발송은 생략합니다.",
                tokens.size(), title);
    }
}
