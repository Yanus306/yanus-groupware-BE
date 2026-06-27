package com.yanus.attendance.chat.application;

import java.util.List;
import java.util.Map;

/**
 * FCM 푸시 발송 추상화.
 * 기본 구현은 {@link NoOpFcmSender}(로그만 남김)이며,
 * Firebase Admin SDK 연동 구현으로 교체하면 실제 발송이 동작한다. (docs/chat-fcm-setup.md 참고)
 */
public interface FcmSender {

    void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data);
}
