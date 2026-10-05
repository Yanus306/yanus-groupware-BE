# yANUs 운영 안정성 개선 기록

## 문제와 선택

Grafana·Prometheus·Loki는 실제 운영되지만 알림 수신지와 외부 점검이 없었고 CD는 JAR 덮어쓰기·프로세스 상태 확인에 그쳤다. 내부 지표와 사용자 관점의 공개 HTTPS를 구분하고, 같은 빌드 후보를 실제 DB/HTTP로 검증한 뒤 실패하면 앱과 실행 설정을 복구하는 것을 목표로 삼았다.

내부 경로는 Prometheus → Alertmanager → yANUs Slack의 yanus-서버-알람이다. 외부는 다른 장애 영역의 GitHub-hosted runner를 먼저 선택했다. 별도 VM의 Blackbox만으로 같은 Hyper-V 전체 중단을 감지할 수 없고, Actions는 무료 실행과 예약 신뢰성이 다르다. 반복 지연·누락 시 외부 감시 서비스로 전환한다.

## 구현과 확인 수준

| 범위 | 증거 | 현재 한계 |
| --- | --- | --- |
| 내부 알림 | 격리 Alertmanager0.28.1의 그룹·중복·PROD·disk inhibition 검증과 실제 native 설치/Slack FIRING·RESOLVED 수신 | 합의한 synthetic 알림 전달이며 실제 고객 장애나 장기 MTTD 증거가 아님 |
| 외부 점검 | 실제 HTTP/상태/전달/artifact/TLS 테스트9개, 공개 API3167ms·Grafana1122ms UP 읽기 관찰 | 기본 브랜치 예약 실행/run ID·실제 전달 전. 감지 시간 보장 없음 |
| 대시보드·규칙 | promtool2.45.3 SUCCESS, 실제 PROD Prometheus100 PromQL 오류0 | 현재 트래픽이 없으면 latency NaN, 빈 ALERTS 정상 |
| 준비 상태 | 격리 Postgres 중단에서 readiness503/liveness200·공개403, 승인 후보의 운영 private commit/readiness/liveness UP | DB 장애 주입은 격리 환경에서만 수행 |
| 배포·복구 | 실제 격리 native systemd 실패/복구 훈련, 승인한4493049 동일 JAR 운영 SUCCESS·실제 Slack 수신·root0700 원본 snapshot | 자동 CD의 기본 브랜치 run은 통합 검증 중. 운영 실패 주입을 복구 증거로 사용하지 않음 |

## 실험과 개선

checksum 오류와 미검토 schema 정책은 교체 전에 거절하고 기존 PID를 보존했다. 실제 flock을 잡은 동시 배포도 거절했다. 잘못된 Start-Class를 가진 JAR는 실제 프로세스가 실패했으며, private 검증120초 제한 뒤 이전 포인터·설정을 복원하고 정상 HTTP를 확인했다. 해당 실험125688ms, 결과 ROLLED_BACK, job exit1이었다. 복구 성공을 후보 배포 성공으로 바꾸지 않았다.

이전 보관 JAR까지 손상시킨 실험은125597ms 후 ROLLBACK_FAILED로 종료했다. 자동 반복을 멈춘 후 수동 복구와 정상 HTTP를 확인했다. build-info가 없는 일반 파일 JAR도126187ms 후 원래 파일·hash로 복구했고 snapshot0700 권한을 확인했다. 이 legacy 실험은 합성 fixture다.

실제 운영 baseline JAR(SHA256460b6e47...)와 외부 라이브러리8개를 별도로 읽기 복사해 격리 Postgres16/configuration/data에서 PropertiesLauncher로 실행했다. 잘못된 새 후보 이후127058ms에 기존 일반 JAR·unit·외부 properties·lib별SHA256·snapshot0700·HTTP를 복원했다. 실제 바이너리를 사용한 격리 복구 증거이며, 운영 설정/고객 데이터/운영 host에 적용한 증거는 아니다.

실제 Actuator가 vendor JSON content type을 반환해 처음에는 정상 준비 상태를 거절했다. application/*+json을 처리하도록 고치고 실제 서비스와 HTTP 회귀로 확인했다. 잘못된 webhook URL 처리와 오래된 artifact 상태도 테스트 실패를 통해 수정했다.

source commit metadata가 없는 기존 JAR의 실제 hash와 migration inventory를 조사했다. 마지막 성공 CD의 e8ea205를 격리 재빌드하여 JAR 내부 파일441개 전체 일치로 재현 가능한 운영 소스 기준을 확보했다. ZIP 포장 hash는 달라 동일 artifact라고 주장하지 않는다. 후보에는 이미 main에 병합된 #200 DTO 정리가 포함된다. migration27개 일치가 전체 API/업무 호환성의 증거를 대신하지 않으며, 후보 활성화 전 별도 검토를 유지한다. #203/V28 배포는 별도 범위다.

monitoring-server의 실제 Alertmanager0.28.1은 loopback9093에서 동작한다. Prometheus7개 scrape와10규칙,5개 Grafana 보드 도움말·datasource를 확인했다. channel-only Incoming Webhook 앱을 설치한 뒤 검증용 알림의 FIRING09:33:49UTC/RESOLVED09:35:29UTC 수신을 확인했다. 제목이 내부 URL을 가리키던 문제는 공개 Grafana 링크로 수정했다. 검증 도중 reload가 있어 전달 시간을 정상 group_interval·MTTD/MTTR로 쓰지 않는다. GitHub Secret은 등록했으며 default-branch Actions run은 남아 있다.

실험별 UTC 시각과 elapsed time은 격리 `scenarios.json`, `legacy.json`, `overlay.json`에 남기고 build/ 바깥에도 보존했다. 최종 Gradle275 tests/0 failures/0 errors/0 skipped·bootJar PASS12s, Node 외부9개/배포3개 PASS를 확인했다. 아직 실제 고객 장애에서 측정한 MTTD/MTTR나 장기 SLO가 아니다. Persona report/finish 상태도 제품 테스트와 구분해 기록한다.

## 후속 작업

2026-10-05 사용자가 #200 DTO와 관측/배포 범위의 후보를 승인하여 `4493049` 동일 JAR(SHA256 `50ec1c9c…b5066`)를 운영에 적용했다. 실제 commit·DB readiness/liveness·메인/공개 JSON·10:41:40UTC 배포 SUCCESS Slack 수신과 Prometheus7/7 UP을 확인했다. 기존 `.env`·V27 유지, 원본 JAR와 실행 설정 snapshot0700 보존, #203/V28 제외다. 서버의 실제 외부 설정 경로가 초기 QA 경로와 달라 `/etc/yanus` 백업·교체·복구를 보완하고, 해당 경로의 실제 격리 rollback125547ms 및 동일 후보 SUCCESS로 먼저 확인했다. 이력 조회 역할은 Flyway SELECT만 허용하고 다른 업무 테이블 SELECT 불가를 확인했다.

다음은 PostgreSQL 백업의 별도 보관·암호화·격리 복원과 RPO/RTO 기록이다. 이후 OpenTelemetry+Tempo로 request/trace·DB/외부 span을 연결하고 샘플링·민감정보·수집 비용을 정한다. 마지막으로 별도 k6 환경에서 대표 API의 P95/처리량/쿼리·풀 병목을 측정해 한 가지 개선 전후를 같은 조건으로 비교한다. 아직 구현되지 않은 작업은 완료 역량으로 표시하지 않는다.
