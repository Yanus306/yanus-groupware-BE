# #223 후속 검증 — 근태 예외 목록 SELECT 6회 → 2회

`GET /api/v1/attendance-exceptions`의 판정용 SELECT 5개를 회원 기준 LEFT JOIN 1개로 합쳤다. 날짜별 일정과 주간 일정의 우선순위, 예외 유형은 기존 Java 코드에서 판정한다. 기존 예외가 여러 유형이면 조인 결과에서 해당 유형을 모두 모으고 회원당 한 번 판정한다. PostgreSQL 고유 키 충돌을 건너뛰는 JDBC INSERT와 저장 후 목록 SELECT는 기존 경로를 사용한다.

## PostgreSQL 호출과 반환 행

| 회원 수 | 시나리오 | SELECT | INSERT | 전체 |
| ---: | --- | ---: | ---: | ---: |
| 10 | 첫 조회 | 6 → 2 | 10 → 10 | 16 → 12 |
| 10 | 재조회 | 6 → 2 | 0 → 0 | 6 → 2 |
| 100 | 첫 조회 | 6 → 2 | 100 → 100 | 106 → 102 |
| 100 | 재조회 | 6 → 2 | 0 → 0 | 6 → 2 |

두 번의 비교에서 PostgreSQL `pg_stat_statements` 호출 수가 일치했다. SELECT가 DB에서 반환한 행의 합계는 100명 첫 조회에 300 → 200행, 재조회에 400 → 200행이었다. 측정 데이터에서 회원마다 예외 하나가 생겼다. 여러 유형이 저장된 회원의 판정용 조인은 유형당 행을 반환하며, 통합 테스트에서 Java가 모든 기존 유형을 집계하는지 확인했다.

## HTTP p95

| 비교 | 100명 첫 조회 | 100명 재조회 |
| --- | ---: | ---: |
| 1차 | 18.82 ms → **10.96 ms** | 9.73 ms → **6.34 ms** |
| 2차 | 13.17 ms → **11.20 ms** | 9.18 ms → **7.28 ms** |

같은 호스트에서 머지된 6회 버전(`ed9f4aba9e0f525c9edb619e543e7fff8a8c1666`)과 개선 작업본을 차례로 기동했다. JDK 21.0.10과 별도 PostgreSQL 16.15 컨테이너(Flyway V27)에 같은 fixture를 사용했다. 버전·시나리오마다 HTTP 동시성 1, 워밍업 20회, 측정 100회로 계산했다. 첫 조회 표본의 예외 초기화 시간은 제외했다. 모든 측정 요청에서 `200`과 응답 100건을 확인했고, 각 시나리오 오류는 0/100건이었다. p95는 로컬 표본 기준이다.

[1차 6회](run-1-baseline.json) · [1차 2회](run-1-optimized.json) · [2차 6회](run-2-baseline.json) · [2차 2회](run-2-optimized.json)

## 동시 조회·필터·요약

- **동시 조회** — 두 INSERT가 같은 테이블 락에 대기한 뒤 락을 해제했다. 두 버전 모두 두 요청이 `200`으로 같은 목록·요약을 반환했고, DB에 예외가 정확히 100건 남았다. 별도 통합 테스트에서도 기존 승인·반려 상태와 메모가 보존됐다.
- **필터와 날짜 전체 요약** — 팀·유형·상태를 같이 지정하면 목록 1건과 `filteredCount=1`이다. 요약의 `totalCount=4`, `openCount=2`, 출근 누락 3, 일정 없음 1은 날짜 전체 기준으로 유지됐다. 잘못된 필터는 `400`과 저장 0건이다.
- **일정과 중복 키** — 날짜별 야간 근무가 주간 일정보다 우선하고, `DAY_OFF`는 주간 일정을 가린다. 같은 회원의 기존 지각·퇴근 누락 두 건을 모두 유지했다. 두 SELECT만 실행됐고 중복 INSERT는 0건이었다.
- **자동 퇴근과 롤백** — 선택한 회원만 자동 퇴근했고, 트랜잭션 롤백 후 새 예외는 남지 않았다.
- **SQL 회귀 테스트** — JWT를 사용하는 실제 HTTP 요청에서 10명·100명의 첫 조회와 재조회를 계측했다. 기존 버전의 SELECT `[6, 6, 6, 6]`에서 실패한 테스트가 개선 후 `[2, 2, 2, 2]`로 통과했다.

Gradle 전체 빌드에서 **43개 스위트, 292개 테스트가 통과**했다. 실패·오류·건너뜀은 0건이다. 클린 빌드로 실행한 Persona Harness 로컬 확인(`workflow finish implement --assurance cooperative`)도 `PASS`다.

## 재현

```bash
YANUS_223_BASELINE_COMMIT=ed9f4aba9e0f525c9edb619e543e7fff8a8c1666 \
  bash docs/reports/evidence/223-attendance-exception-query/run-comparison.sh /tmp/yanus-223-select2
./gradlew test --tests '*AttendanceExceptionQueryIntegrationTest'
./gradlew build
```
