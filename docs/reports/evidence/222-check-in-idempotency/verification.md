# #222 출근 멱등 처리 검증

- 날짜는 2026-11-17이다. 수정 전후 모두 JDK 21과 로컬 Spring Boot HTTP 서버, PostgreSQL 16.15 Testcontainers, Flyway V27을 사용했다.
- 수정 전 소스는 원격 `dev`의 `7759f26de489449ca114d808ae91362a9b88f6ac`이고, 수정 후 소스는 해당 커밋에서 시작한 `codex/att-222-check-in-idempotency`의 로컬 작업본이다. [이슈 #222](https://github.com/Yanus306/yanus-groupware-BE/issues/222)

| 같은 경로에 보낸 시나리오 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 최초 출근 뒤 같은 회원이 재요청 | `200` → `400 ALREADY_CHECKED_IN` | `200` → `200`, 동일 ID와 최초 시각, DB 한 건 |
| 두 INSERT가 같은 DB 잠금에서 대기한 뒤 동시에 진행 | 3회 모두 `200` 한 건, `500 INTERNAL_ERROR` 한 건 | 3회 모두 `200` 두 건, 동일 ID와 최초 시각, DB 한 건 |
| JWT 없이 요청하거나 허용되지 않은 IP로 재요청 | 각각 `403` | 각각 `403` |

수정 전에는 DB 고유 제약이 중복 저장을 막았지만 충돌한 요청의 응답은 `500`이었다. 수정 후에는 같은 제약을 대상으로 `ON CONFLICT DO NOTHING`을 사용하고, 별도 SELECT로 실제 행을 읽어서 응답한다. JDBC INSERT의 시각 바인딩을 기존 Hibernate 설정에 맞춰 같은 데이터 소스에서 읽을 때 이전 JPA 기록과 같은 방식으로 해석되도록 했다.

[수정 전 원본](results.json) · [수정 후 원본](results-after.json) · [수정 전 새 테스트의 실패 기록](gradle-red.json) · [고유 제약 오류](constraint-errors.log)

## 재검증

HTTP 통합 테스트는 순차 재시도, 두 요청의 쓰기 경합, 선행 커밋과 롤백, 기존 퇴근 기록, 서울 자정 전후·다른 회원, 인증·IP 거절을 각각 검사한다. `pg_stat_activity`는 별도 연결에서 조회해 기다리는 INSERT를 확인한다. 자정 경계의 출근 시각은 고정해 PostgreSQL의 마이크로초 정밀도까지 검사한다.

JDK 21 전체 Gradle 빌드에서 42개 테스트 클래스의 **285개 테스트**가 통과했다. 실패·오류·건너뜀은 0개였다. JVM을 UTC로 설정한 별도 실행에서도 출근 HTTP 통합 테스트 7개가 통과했다.

Persona 구현·리뷰 보고서를 채우고 티켓을 보관했다. 수정 전 실패와 검증 과정에서 해결된 실패의 도구 기록은 티켓 이력에 보존했다. 빌드 산출물을 정리한 뒤 `npx ph workflow finish implement --assurance cooperative`를 새로 실행해 `Finish status: PASS`를 확인했다.

```bash
./gradlew test --tests '*AttendanceServiceTest' --tests '*AttendanceCheckInHttpIntegrationTest'
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew test --tests '*AttendanceCheckInHttpIntegrationTest'
./gradlew build
```

독립 실행 프로그램은 [수정 전 재현 방법](reproduction.md)의 Java와 Gradle 파일을 사용한다. 개선된 작업본에서 실행할 때는 소스 추출·이동을 생략해 현재 작업본에서 기동하고, 같은 Gradle 명령에 `YANUS_222_EXPECT_IDEMPOTENT=true`를 추가한다. 프로그램은 양쪽 HTTP 응답의 기록 ID와 시각이 같은지, DB 행이 한 건인지도 검사한다.
