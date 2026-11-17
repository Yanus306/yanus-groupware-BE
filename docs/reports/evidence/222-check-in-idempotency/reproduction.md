# #222 수정 전 출근 API 재현

2026-11-17 11:23 KST에 원격 `dev` 커밋 `7759f26de489449ca114d808ae91362a9b88f6ac`을 임시 폴더에 추출해 실행했다. JDK 21.0.10, PostgreSQL 16.15 Testcontainers, Flyway V27, `READ COMMITTED` 환경이다. Spring Boot는 임의의 loopback 포트에서 실행했고, 테스트 회원과 토큰을 사용했다. 요청은 `POST /api/v1/attendances/check-in`이다.

| 시나리오 | #222의 기대 | 수정 전 관측 | DB 결과 |
| --- | --- | --- | --- |
| 같은 날 재시도 | 같은 ID·시각으로 `200` 두 번 | 첫 요청 `200`, 재시도 `400 ALREADY_CHECKED_IN` | 회원과 업무일의 기록 1건 |
| 동시 요청 1회차 | 같은 ID·시각으로 둘 다 `200` | A `200`, B `500 INTERNAL_ERROR` | 기록 1건 |
| 동시 요청 2회차 | 동일 | A `500 INTERNAL_ERROR`, B `200` | 기록 1건 |
| 동시 요청 3회차 | 동일 | A `200`, B `500 INTERNAL_ERROR` | 기록 1건 |
| JWT 없이 요청 / 등록 후 허용되지 않은 IP로 요청 | 기존 접근 거부 | 각각 `403`, IP 거절 코드는 `INVALID_ATTENDANCE_IP` | 기존 기록 1건 |

## 경합을 재현한 방법

1. 각 회차에 출근 기록이 없는 회원을 만들었다. 두 요청은 같은 JWT와 허용 IP를 사용했다.
2. 별도 DB 트랜잭션에서 `LOCK TABLE attendance IN SHARE MODE`를 걸었다. 이 락에서는 조회가 가능하고 INSERT는 대기한다.
3. 두 HTTP 요청을 보내고 `pg_stat_activity`와 `pg_blocking_pids`로 같은 락에 대기 중인 `INSERT INTO attendance` 두 개를 확인했다. 이 관측을 세 회차 각각 JSON에 기록했다.
4. 락을 풀고 두 HTTP 응답과 커밋된 행을 확인했다. 세 회차의 실패 로그 모두 SQLSTATE `23505`와 `attendance_member_id_work_date_key` 제약을 가리켰다.

두 요청이 이미 INSERT에 도달했으므로 출근 존재 여부를 조회하는 단계는 둘 다 통과한 상태였다. 고유 제약이 중복 저장을 차단했고, 충돌한 요청은 공통 예외 처리기를 거쳐 `500`이 됐다. 업무일 중복 판단과 쓰기가 분리된 수정 전 흐름에서 발생한 결과다.

[HTTP 응답·DB 결과 원본](results.json) · [DB 오류 로그 발췌](constraint-errors.log) · [재현 코드](CheckInBaselineProbe.java)

## 재실행

저장소 루트에서 실행한다. Docker와 JDK 21이 필요하다. Gradle 캐시가 준비돼 있으면 `--offline`으로 실행할 수 있다. 셸은 종료 시 임시 추출본과 출력 파일을 정리한다.

```bash
(
  set -eu
  evidence_dir="$PWD/docs/reports/evidence/222-check-in-idempotency"
  baseline=7759f26de489449ca114d808ae91362a9b88f6ac
  probe_dir="$(mktemp -d)"
  trap 'rm -rf "$probe_dir"' EXIT
  mkdir "$probe_dir/source"
  cp "$evidence_dir/CheckInBaselineProbe.java" "$evidence_dir/probe.init.gradle" "$probe_dir/"
  git archive "$baseline" | tar -x -C "$probe_dir/source"
  cd "$probe_dir/source"
  YANUS_222_PROBE_DIR="$probe_dir" GITHUB_SHA="$baseline" JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
    ./gradlew --console=plain -I "$probe_dir/probe.init.gradle" checkInBaselineProbe
)
```

재현 프로그램의 `BUILD SUCCESSFUL`은 위 수정 전 증상이 확인됐다는 뜻이다. 요청 A와 B 중 어느 쪽이 성공하는지와 기록 ID·시각은 실행마다 달라질 수 있다.

제안한 `ON CONFLICT DO NOTHING`과 독립된 `SELECT`는 별도 격리 PostgreSQL 16.15의 두 세션으로 확인했다. 선행 세션 커밋 시 후행 INSERT는 0행이고 최초 시각을 읽었다. 선행 롤백 시 후행 INSERT는 1행이고 자신이 저장한 시각을 읽었다. 실제 API의 개선 후 동작은 구현 단계의 통합 테스트에서 확인한다. [구현 계획](../../../implement-plan/222-check-in-idempotency.md)
