# #223 근태 예외 목록 개선 전 재현

2026-11-17 13:15 KST에 원격 `dev`의 `210b46324bef885350db6c8e64ee38ab3c1c581b`을 실행했다. JDK 21.0.10, 격리 PostgreSQL 16.15, Flyway V27, `READ COMMITTED` 환경이다. JVM과 PostgreSQL은 한 실험에만 사용했다.

## 한 번의 서비스 호출에서 실행된 SQL

같은 팀의 활성 회원에게 매주 월요일 09:00~18:00 근무만 배정했다. 시험할 날짜에 출퇴근·개별 일정·기존 예외가 없다. 10명은 2026-10-05, 100명은 2026-10-12를 조회했다. 첫 호출에서 회원마다 출근 누락 예외 한 건을 생성한 뒤, 같은 날짜를 다시 호출했다.

| 회원 | 첫 조회 Hibernate/DB 합계 | PostgreSQL 쿼리 구분 | 재조회 Hibernate/DB 합계 | 재조회 PostgreSQL | 반환·저장 건수 |
| ---: | ---: | --- | ---: | --- | ---: |
| 10명 | 73 | SELECT 63, INSERT 10 | 63 | SELECT 63, INSERT 0 | 모두 10건 |
| 100명 | **703** | **SELECT 603, INSERT 100** | **603** | **SELECT 603, INSERT 0** | 모두 100건 |

호출 직전 Hibernate 통계와 `pg_stat_statements`를 각각 초기화했다. 서비스 프록시의 `getList(date, null, null, null)`을 호출해 트랜잭션이 끝난 뒤, Hibernate prepared statement 수와 PostgreSQL의 SQL 유형별 호출 수를 읽었다. 관측 SQL·트랜잭션 제어문·fixture 준비는 집계에서 제외했다. 이 표는 SQL 횟수이며 HTTP 지연 시간은 별도 실험으로 측정한다.

100명일 때 날짜별 일정 조회 200회, 주간 일정 조회 200회, 출결 조회 100회, 예외 존재 확인 100회였다. 전체 회원·당일 예외 목록·팀 조회가 각각 한 번씩 더해졌다. 두 일정 조회가 200회씩인 이유는 생성 판정과 응답 변환 양쪽에서 같은 일정을 조회하기 때문이다. [정규화 SQL과 실제 실행 횟수](baseline.json)

## 동시 첫 조회

100명 fixture에서 처음 조회하는 2026-10-19에 실제 JWT HTTP GET 두 건을 보냈다. 별도 연결에서 `LOCK TABLE attendance_exception IN SHARE MODE`를 걸어 읽기는 통과시키고 쓰기를 대기시켰다. `pg_blocking_pids`로 두 INSERT가 모두 대기하는 것을 확인하고 잠금을 풀었다.

- 한 요청은 `200`과 예외 100건을 반환했다. 다른 요청은 `500 INTERNAL_ERROR`였다.
- PostgreSQL 로그에 SQLSTATE `23505`와 `uk_member_workdate_type`이 남았다. 커밋된 예외는 100건이었다.

[HTTP 상태·잠금 관측 JSON](baseline.json) · [고유 제약 오류 로그](constraint-errors.log)

## 재실행 방법

저장소 루트에서 실행한다. JDK 21, Docker, `postgres:16.15` 이미지와 Gradle 의존성이 필요하다. 같은 소스의 임시 추출본을 기동하며, 커밋된 기준값 파일과 별도로 임시 디렉터리에 결과를 저장한다.

```bash
(
  set -eu
  evidence="$PWD/docs/reports/evidence/223-attendance-exception-query"
  baseline=210b46324bef885350db6c8e64ee38ab3c1c581b
  scratch="$(mktemp -d)"
  printf 'Reproduction workspace: %s\n' "$scratch"
  mkdir "$scratch/source"
  cp "$evidence/ExceptionQueryBaselineProbe.java" "$evidence/probe.init.gradle" "$scratch/"
  git archive "$baseline" | tar -x -C "$scratch/source"
  cd "$scratch/source"
  YANUS_223_PROBE_DIR="$scratch" GITHUB_SHA="$baseline" JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
    ./gradlew --console=plain -I "$scratch/probe.init.gradle" exceptionQueryBaselineProbe
)
```

의존성이 로컬에 캐시돼 있으면 `--offline`을 붙일 수 있다. `baseline.json`은 출력된 임시 디렉터리에 생성된다. 두 HTTP 요청 중 어느 쪽이 먼저 성공하는지와 백엔드 PID는 실행마다 달라질 수 있다. [실행 코드](ExceptionQueryBaselineProbe.java) · [Gradle 초기화 스크립트](probe.init.gradle)

개선본과 같은 조건으로 SQL 및 HTTP p95를 함께 재실행하려면 [수정 전후 검증 기록](verification.md)의 비교 명령을 사용한다.
