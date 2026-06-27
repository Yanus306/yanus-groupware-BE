# 채팅 FCM 푸시 설정 가이드

오프라인 사용자에게 새 메시지를 FCM(Firebase Cloud Messaging) 푸시로 알리기 위한 연동 가이드입니다.
현재 코드는 **골격(scaffold)** 상태로, 기본 발송 구현은 로그만 남기는 `NoOpFcmSender`입니다.
아래 단계를 완료하면 실제 발송이 동작합니다.

## 현재 상태
- `FcmSender` 인터페이스로 발송을 추상화 (`application/FcmSender.java`)
- 기본 빈: `NoOpFcmSender` — 로그만 출력, 외부 의존성/키 불필요 (서버 기동·테스트에 영향 없음)
- 디바이스 토큰 등록/해제 API: `POST/DELETE /api/v1/fcm/tokens`
- 라우팅: 새 메시지 발생 시 온라인 멤버는 SSE, **오프라인 + 음소거 안 한 멤버**의 토큰을 모아 `FcmSender.sendToTokens` 호출

## 실제 연동 단계

### 1. Firebase 프로젝트 / 서비스 계정 준비
1. Firebase 콘솔에서 프로젝트 생성
2. 프로젝트 설정 → 서비스 계정 → **새 비공개 키 생성** → `service-account.json` 다운로드
3. 키 파일은 **커밋하지 말 것** (환경변수/시크릿으로 주입)

### 2. 의존성 추가 (`build.gradle`)
```gradle
implementation 'com.google.firebase:firebase-admin:9.4.1'
```

### 3. FirebaseApp 초기화 + 발송 구현 추가
`infrastructure/FirebaseFcmSender.java` (빈 이름 `firebaseFcmSender`)를 추가하면
`@ConditionalOnMissingBean(name = "firebaseFcmSender")`가 걸린 `NoOpFcmSender`가 자동으로 비활성화됩니다.

```java
@Slf4j
@Component("firebaseFcmSender")
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "true")
public class FirebaseFcmSender implements FcmSender {

    @PostConstruct
    void init() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(System.getenv("FCM_CREDENTIALS_PATH")));
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials).build());
        }
    }

    @Override
    public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build();
        FirebaseMessaging.getInstance().sendEachForMulticast(message);
    }
}
```

### 4. 환경변수
```
FCM_ENABLED=true            # application.yml의 fcm.enabled로 매핑
FCM_CREDENTIALS_PATH=/run/secrets/service-account.json
```

### 5. 프론트엔드
- Firebase Web SDK 설치 및 `firebase-messaging-sw.js`(service worker) 등록
- 권한 요청 후 토큰 발급 → `POST /api/v1/fcm/tokens { token }` 등록, 로그아웃/토큰 갱신 시 갱신
- 백그라운드 메시지 수신 처리, 알림 클릭 시 `data.channelId`로 딥링크

## 참고
- 음소거(알림 끔)는 서버에 영속화되어 있으며(`channel_mute`), FCM 라우팅에서 음소거 멤버는 자동 제외됩니다.
- 온라인 판단 기준은 SSE 연결 보유 여부(`ChatSseService.isOnline`)입니다.
