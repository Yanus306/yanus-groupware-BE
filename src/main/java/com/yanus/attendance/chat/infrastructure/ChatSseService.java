package com.yanus.attendance.chat.infrastructure;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 멤버별 SSE 연결(SseEmitter)을 관리한다.
 * 연결이 하나라도 있으면 해당 멤버를 "온라인"으로 간주한다.
 */
@Service
public class ChatSseService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(memberId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(throwable -> remove(memberId, emitter));

        send(emitter, "connected", "ok", () -> remove(memberId, emitter));
        return emitter;
    }

    public boolean isOnline(Long memberId) {
        List<SseEmitter> list = emitters.get(memberId);
        return list != null && !list.isEmpty();
    }

    public void sendToMember(Long memberId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            send(emitter, eventName, data, () -> remove(memberId, emitter));
        }
    }

    private void send(SseEmitter emitter, String eventName, Object data, Runnable onFailure) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            onFailure.run();
        }
    }

    private void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(memberId);
        }
    }
}
