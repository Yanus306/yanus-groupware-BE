# #232 인증 메일 발송 분리 검증 기록

2026-11-24 KST 격리 실험. 기준은 `dev@7610b37`이고 후보는 같은 기준의 `feat/#232` 작업 트리다. 원격 운영 계정·실제 SMTP·운영 트래픽을 사용하지 않았다. 전체 소스 변경은 작업 공간에 따로 저장한 패치로, 구현 결정은 [코드 읽는 순서](code-walkthrough.md)로 확인한다.

측정 JAR의 SHA-256은 기준 `77d0f30394db58eb653ff76affab6605373e08b0d0fd7783f09adaf32717b62f`, 후보 `e77fe4d5a13de9a1be74f52695fb4192bca230ef86f39389cd8ba8e9cb816fc3`이다.

## 재현과 원인

1. 현재 버전에서 SMTP DATA 완료 응답을 1,200ms 지연했다. 단일 가입에 **1,363.28ms**가 걸렸고, SMTP 대기 중 Hikari active는 **1**이었다. 별도 DB 연결에서 회원은 커밋 전이라 조회되지 않았다.
2. 로컬 SMTP가 `451`을 반환했을 때 기존 가입은 HTTP 500이고 회원은 롤백됐다.
3. 테스트용 PostgreSQL 지연 제약 트리거를 커밋에만 실패하도록 추가했다. 기존 가입은 **SMTP가 메일을 이미 수락한 상태에서 HTTP 500과 회원 롤백**으로 끝났다. [변경 전 HTTP와 DB 관측](results/baseline-contract.json)

## Gatling과 Grafana 동시 관측

Java 21, Docker PostgreSQL 16.15, 앱 JVM 4 logical processors와 Xmx768m, Hikari 4개, 동일 SMTP 타임아웃, 시험용 메일 워커 1개와 100ms 폴링 간격. 앱·부하 생성기·Docker는 같은 개발 머신에서 실행했다. 서비스 기본 폴링 간격은 1,000ms다.

각 회차는 6초 동안 가입 2건/초, 재발송 2건/초, 별도 활성 계정의 `/me` 6건/초를 주입한다. 가입 이메일은 매번 고유하고 재발송은 미인증 계정을 한 번씩 사용한다. 회차당 HTTP 60건 중 가입 12건, 재발송 12건, `/me` 36건. 정상 SMTP는 한 쌍을 실행했고, 지연 SMTP는 기존→개선→개선→기존 순으로 두 번 비교했다. 지연은 SMTP의 DATA 응답에서 주입했다. 피크는 Grafana를 통해 Prometheus에 0.5초 간격으로 조회했으며 HTTP 대기는 Gatling으로 측정했다.

| SMTP 1.2초 지연 | 기존 A | 개선 A | 기존 B | 개선 B |
| --- | ---: | ---: | ---: | ---: |
| 가입 HTTP p95 | 2,553ms | 90ms | 2,558ms | 94ms |
| 재발송 HTTP p95 | 2,615ms | 18ms | 2,622ms | 19ms |
| 별도 활성 계정 `/me` HTTP p95 | 1,874ms | 13ms | 1,769ms | 15ms |
| Hikari pending 피크 | 13 | 0 | 12 | 0 |
| Hikari 연결 획득 누적 시간 | 50,471ms | 4ms | 51,312ms | 3ms |
| HTTP 성공률 | 60/60 | 60/60 | 60/60 | 60/60 |
| 이번 실행의 SMTP 수락 | 24/24 | 24/24 | 24/24 | 24/24 |
| 작업 저장 → `SENT` p95 | 해당 없음 | 25.717s | 해당 없음 | 25.338s |

Hikari 누적 시간은 Gatling 명령 시작 전부터 보고서 생성 직후까지 수집한 풀 타이머의 차분이다. 개선 버전은 그 구간의 워커 조회도 포함한다. `SENT`는 SMTP 수락 후 DB 기록 시점이며 메일함 도착을 측정하지 않았다. 워커 한 개가 느린 SMTP와 직렬로 통신하므로 이 유입률에서는 작업이 밀린다. 정상 SMTP에서 가입 p95는 **98ms → 91ms**, 가입 대기 상태는 두 버전 모두 Hikari pending 피크 0, 개선 버전의 작업 저장 → `SENT` p95는 **236ms**였다. 회차당 표본이 적어 정상 조건의 차이를 서비스 개선율로 해석하지 않는다. [정상 기존](results/baseline-normal-a.json) · [정상 개선](results/candidate-normal-a.json)

- A회차 [기존 HTTP와 대기](results/baseline-slow-a.json), [개선 HTTP와 대기](results/candidate-slow-a.json), [Grafana 기존 원시 응답](results/baseline-slow-a-grafana.json), [Grafana 개선 원시 응답](results/candidate-slow-a-grafana.json)
- B회차 [기존 HTTP와 대기](results/baseline-slow-b.json), [개선 HTTP와 대기](results/candidate-slow-b.json), [Grafana 기존 원시 응답](results/baseline-slow-b-grafana.json), [Grafana 개선 원시 응답](results/candidate-slow-b-grafana.json)

## 장애·정합성·복구

| 시나리오 | 확인한 결과 |
| --- | --- |
| 커밋과 롤백 | 작업 INSERT를 DB 제약으로 실패시키면 회원·토큰도 0건. 같은 트랜잭션을 강제로 롤백해도 3개 테이블 모두 0건 |
| 가입 접수 직후 프로세스 종료 | 워커를 잠시 중지한 상태에서 가입 200 뒤 SIGKILL. 회원·작업이 `PENDING`으로 남아 재시작 후 SMTP 1회 수락. SMTP 대기 중 Hikari active 0. 코드 검증 후 회원 `ACTIVE`. [실행 결과](results/candidate-probe.json) |
| SMTP 일시·영구 오류 | 실제 격리 SMTP가 451을 반환했을 때 재시도 후 2번째 발송 성공. 550은 1번째에 `FAILED`. [실행 결과](results/delivery-failure.json) |
| SMTP 수락 직후 프로세스 종료 | 완료 UPDATE를 DB 행 잠금으로 멈추고 SMTP 수락을 확인한 뒤 SIGKILL. 기한을 지난 상태로 만들어 재시작하니 SMTP가 총 2회 수락. 첫 토큰 인증 200, 같은 토큰 재인증 400. 실험에서 재시작 대기 단축을 위해 테스트 DB의 선점 만료 시각을 조정했다. [실행 결과](results/delivery-failure.json) |
| 재발송·검증 경합 | 같은 회원을 기준으로 동시 요청을 실행했다. 최신 토큰 하나만 남고, 교체된 토큰으로 인증할 수 없었다. 두 워커 중 하나만 선점하고 만료된 선점 ID로는 상태를 갱신할 수 없었다. |
| Flyway 업그레이드·이전 JAR 복구 | 기존 V28 DB에 V29를 적용해 이전 토큰을 사용했다. 이후 이전 JAR도 같은 DB에서 기동했고 새 JAR로 복귀하자 미처리 작업이 발송됐다. 운영 CD의 스키마 변경 승인 및 적용 절차는 별도다. [실행 결과](results/migration-rollback.json) |

## 실행 명령

작업 공간의 기준 JAR와 후보 JAR을 먼저 준비한다. Gatling 의존성을 Maven 캐시에 설치한 뒤 스크립트를 실행한다. 각 명령은 loopback의 앱·SMTP와 별도의 PostgreSQL 컨테이너를 준비한다. 부하 모드는 로컬 Prometheus와 Grafana도 생성·정리한다.

```sh
./gradlew build
mvn -f docs/reports/evidence/232-email-verification/gatling/pom.xml test-compile
lab=docs/reports/evidence/232-email-verification/lab.mjs
node "$lab" ../baseline.jar baseline ../baseline-contract
node "$lab" ../candidate.jar candidate ../candidate-probe
node "$lab" ../candidate.jar delivery-failure ../delivery-failure
node "$lab" ../candidate.jar upgrade ../migration-rollback ../baseline.jar
YANUS_LAB_SMTP_DELAY_MS=0 node "$lab" ../baseline.jar load ../baseline-normal-a
YANUS_LAB_SMTP_DELAY_MS=0 node "$lab" ../candidate.jar load ../candidate-normal-a
YANUS_LAB_SMTP_DELAY_MS=1200 node "$lab" ../baseline.jar load ../baseline-slow-a
YANUS_LAB_SMTP_DELAY_MS=1200 node "$lab" ../candidate.jar load ../candidate-slow-a
YANUS_LAB_SMTP_DELAY_MS=1200 node "$lab" ../candidate.jar load ../candidate-slow-b
YANUS_LAB_SMTP_DELAY_MS=1200 node "$lab" ../baseline.jar load ../baseline-slow-b
```

백엔드 전체 303개, 프론트 인증 관련 23개 테스트가 통과했다. 백엔드·프론트 빌드가 통과했다. 부하 실험은 지연에서 발생하는 병목을 재현한다. 이 수치를 운영 피크나 최대 처리량으로 사용하려면 별도의 운영 측정이 필요하다.

Persona Harness는 구현·리뷰 리포트와 코드 읽기 기록을 확인했다. 종료 게이트는 별도 실행 세션의 loop 상태가 초기화되지 않아 보류됐고, 티켓도 열려 있다.
