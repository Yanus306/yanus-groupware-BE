# #232 변경 코드 읽는 순서

`feat/#232`의 기준은 `dev@7610b37`이다. 메일 발송을 작업 큐로 옮기고 DB 트랜잭션 경계를 분리한 구현이다.

## 1. 가입 요청에서 한 줄이 바뀐다

`AuthService.register()`는 팀·이메일을 확인하고 회원을 저장한다. 뒤에서 호출하는 메서드가 바뀐다.

```diff
 Member saved = memberRepository.save(member);
-emailVerificationService.sendVerification(saved.getId(), saved.getEmail());
+emailVerificationService.enqueueVerification(saved.getId());
```

기존 `sendVerification()`의 마지막 줄은 `emailService.sendVerificationEmail(email, token)`이었다. 새 코드는 토큰을 저장하고 작업 테이블에 토큰 ID를 등록한다.

```java
public void enqueueVerification(Long memberId) {
    tokenRepository.deleteByMemberId(memberId);
    var token = EmailVerificationToken.create(UUID.randomUUID().toString(), memberId,
            LocalDateTime.now().plusMinutes(30));
    jobs.enqueue(tokenRepository.save(token).getId());
}
```

```sql
INSERT INTO email_verification_job (token_id) VALUES (?);
```

`jobs.enqueue()`의 `Propagation.MANDATORY`가 기존 트랜잭션 밖에서 호출되는 실수를 막는다. PostgreSQL 통합 테스트에서 기존 JPA 회원·토큰 저장과 새 JDBC 작업 INSERT가 함께 커밋·롤백되는 것을 확인한다. 그 트랜잭션이 커밋되면 API가 응답한다.

## 2. 워커가 대기 작업을 하나 선점하고 커넥션을 반납한다

새 `EmailVerificationJobStore`는 한 SQL에서 가져올 행을 고르고 처리 중으로 바꾼다. 아래는 핵심 SQL이다.

```sql
WITH candidate AS (
    SELECT id
    FROM email_verification_job
    WHERE (status = 'PENDING' AND next_attempt_at <= now())
       OR (status = 'PROCESSING' AND lease_until <= now())
    ORDER BY CASE WHEN status = 'PENDING' THEN next_attempt_at ELSE lease_until END, id
    LIMIT 1 FOR UPDATE SKIP LOCKED
)
UPDATE email_verification_job j
SET status = 'PROCESSING', attempt_count = j.attempt_count + 1,
    lease_id = ?, lease_until = now() + (? * interval '1 second'), updated_at = now()
FROM candidate
WHERE j.id = candidate.id
RETURNING j.id, j.token_id, j.lease_id, j.attempt_count, j.created_at;
```

두 인스턴스가 동시에 실행해도 먼저 잡힌 행은 건너뛴다. 선점을 커밋하면 행 락이 풀린다. 이후의 소유권은 60초 기한과 각 실행의 `lease_id`가 나타낸다. 결과 저장 SQL은 `WHERE id = ? AND status = 'PROCESSING' AND lease_id = ?`를 사용해, 뒤늦게 돌아온 원래 실행이 회수한 실행의 상태를 덮는 것을 막는다.

## 3. SMTP 대기는 워커에서 DB 트랜잭션 없이 일어난다

`EmailVerificationWorker.poll()`은 트랜잭션이 없는 전용 스케줄러 메서드다. `claimNext()`와 `loadDelivery()`는 각각 프록시를 거쳐 필요한 동안만 트랜잭션을 연다. `loadDelivery()`가 끝난 후 기존 `EmailService.sendVerificationEmail()`을 부른다. 받는 주소와 토큰을 기존 테이블에서 읽어 오므로 작업 행에는 토큰 문자열을 복제하지 않는다.

```text
트랜잭션 A [선점하고 커밋]
  → 트랜잭션 B [토큰·회원 검증 및 조회 종료]
  → SMTP [워크 스레드에서 대기]
  → 트랜잭션 C [SENT 또는 재시도·실패 기록]
```

일시 오류는 10초, 30초, 2분 간격으로 최대 세 번 재시도한다. 남은 토큰 유효기간도 확인한다. SMTP 5xx, 인증 실패, 준비 실패는 실패 상태로 종료한다. 토큰이 교체·만료되거나 사용된 작업은 취소한다. SMTP가 수락한 직후 서버가 종료되면 같은 메일이 다시 갈 수 있다. SMTP 수락과 PostgreSQL의 완료 기록은 각 시스템에서 처리된다.

## 4. 재발송과 인증은 같은 회원 행을 잠근다

새 토큰을 만드는 `resend()`와 기존 토큰을 쓰는 `verify()`는 둘 다 회원 행에 `PESSIMISTIC_WRITE` 잠금을 건다. 검증은 회원 ID만 먼저 읽고, 잠금을 획득한 뒤 토큰을 다시 읽는다.

- 재발송이 먼저 커밋하면 이전 토큰은 없어지고 발송 작업의 FK는 `NULL`이 된다. 이전 코드로 인증할 수 없다.
- 인증이 먼저 커밋하면 회원은 `ACTIVE`가 된다. 뒤의 재발송 요청은 기존 200 응답 형식으로 종료하고 새 작업을 만들지 않는다. 비활성 회원도 발송 작업을 만들지 않는다.

## 5. 파일별 전체 변경 범위

| 파일 | 역할 |
| --- | --- |
| `auth/application/AuthService.java`, `EmailVerificationService.java` | SMTP 직접 호출을 작업 저장으로 교체하고, 재발송과 인증 경합을 처리 |
| `auth/application/EmailService.java`, `EmailVerificationWorker.java` | 기존 SMTP 전송을 재사용하고 메일 발급 시점 문구, 선점·재시도·종료 정책·계측을 구현 |
| `auth/domain/EmailVerificationJobRepository.java`, `auth/infrastructure/EmailVerificationJobStore.java` | 작업 저장 계약과 JDBC 트랜잭션 구현 |
| `auth/infrastructure/EmailVerificationWorkerConfiguration.java`, `application.properties` | 전용 스케줄러와 SMTP 타임아웃 설정 |
| `auth/domain/EmailVerificationTokenRepository.java`, `auth/infrastructure/EmailVerificationTokenJpaRepository.java`, `EmailVerificationTokenSpringDataRepository.java` | 토큰 ID 조회와 잠금 전 회원 ID 조회 |
| `member/domain/MemberRepository.java`, `member/infrastructure/MemberJpaRepository.java` | 회원 행 잠금 |
| `db/migration/V29__create_email_verification_job.sql` | 작업 상태, 토큰과의 관계, 두 선점 인덱스, 제약조건 |
| 백엔드 통합 테스트, 기존 테스트 및 fake 저장소 | PostgreSQL 커밋·롤백, 중복 선점, 재발송·인증 경합, 만료, 오류 후 재시도 |
| 프론트 `VerifyEmail` 화면과 기존 테스트 | 성공 안내를 발송 완료에서 발송 요청 접수로 변경 |
| `lab.mjs`, Gatling 시뮬레이션 | SMTP 제어, PostgreSQL·Grafana 격리 환경, 실제 HTTP·재시작·전후 부하 측정 |
