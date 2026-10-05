# yANUs 배포 검증과 앱 복구

#205 구현 후 사용자가 검토한 후보 범위를 승인하여 2026-10-05 `4493049` 동일 JAR를 app-server에 적용했다. private 실행 commit·DB readiness/liveness·메인/공개 JSON·실제 Slack SUCCESS 수신을 확인했고 기존 JAR·설정은 root0700 snapshot에 보존했다. helper·보호된 baseline·이력 전용 DB 역할·고정 SSH host key를 준비한 뒤 `YANUS_DEPLOY_ENABLED=true`를 설정했다. 기본 브랜치 자동 CD run은 통합 후 확인한다. #203/V28은 제외하고 V27을 유지한다.

## 동일 후보와 버전

CD candidate job은 `./gradlew test bootJar`를 한 번 실행하고 JAR·manifest·JUnit reports를 같은 artifact로 보관한다. `YANUS_RELEASE_BUILD=true`이면 전체 `GITHUB_SHA`가 필요하다. JAR의 build-info `build.commit`, manifest commit/size/SHA256/migration inventory를 교차 확인한다. deploy job은 해당 artifact를 내려받고 다시 빌드하지 않는다. 관리 `/actuator/info`의 실제 프로세스 commit도 일치해야 한다.

기본 설정과 외부 observability properties 모두 관리 포트9091 loopback·details/components never를 사용한다. readiness는 readinessState+실제 DB 연결, liveness는 livenessState다. `/actuator/info`는 관리 포트에서만 허용되며 공개 Nginx scrape proxy에 추가하지 않는다. readiness 성공만으로 배포를 완료하지 않고 main8080의 OpenAPI JSON 계약과 공개 HTTPS 계약을 검사한다.

## 활성화 전 조건

root 전용 `/etc/yanus-deploy/baseline.json`(0600)에 현재 JAR SHA256, 실제 확인한 sourceCommit, runningCommit 또는 legacy null, 정렬된 Flyway history JSON의 SHA256, 명시적인 compatibilityApproved를 기록한다. `baseline.example.json`의 미확인 값은 그대로 실행할 수 없다. 운영 JAR에 build-info가 없으므로 소스 연결/전체 API와 실행 설정 검토가 필요하다. CD 성공 run 시각과 JAR mtime만으로 sourceCommit을 채우지 않는다.

`psql service=yanus-deploy`는 보호된 `.pg_service.conf`/`.pgpass`로 기존 DB metadata만 읽는다. 해당 DB 역할에는 CONNECT와 flyway_schema_history SELECT 권한만 준다. QA의 root PostgreSQL 역할은 폐기 가능한 컨테이너 전용이며 운영 설정으로 복사하지 않는다.

후보 JAR의 migration script/버전/SHA256/Flyway CRC32는 이전 JAR와 같아야 하고, 현재 SQL history의 성공 여부·체크섬과 맞아야 한다. history fingerprint 변경도 차단한다. 이번 자동 앱 복구는 **schema unchanged만 지원**한다. 새로운 additive migration도 별도 검토·검증 절차로 확장하기 전에는 차단한다. DB downgrade·Flyway disable/repair는 실행하지 않는다.

2026-10-05 운영 읽기 관찰: 기존 JAR SHA256 `460b6e4733f196628d089280fd727d8af2a6a1015b61d3fddd921d6df0e2778a`, commit metadata 없음, migration27개가 후보와 일치. 마지막 성공 CD의 `e8ea2055fa8da6b17f846bf6a79cea8cbb3c7371`을 격리 재빌드하여 운영 JAR의 내부 파일441개(클래스·리소스231개, 라이브러리107개 포함)가 모두 같음을 확인했다. 재현 가능한 source 기준이며 signed provenance 또는 외부 ZIP 해시 동일성의 증거는 아니다. 정렬된 내부 파일 내용의 canonical SHA256는 양쪽 모두 `97b37fc1570af868f609c5b6b7a94079bb45ab37f2999de2b9cbb1d1f8374ae9`이다. 전체 후보의 업무/API 호환성 검토와 운영 활성화는 별도다.

후보에는 현재 main에 병합된 #200 DTO 정리와 #201/#202 관측 기능, #205 버전/readiness·배포 복구를 포함한다. main 대비 추가 Java/resources/build 변경은 관측·배포 계약 범위이며 #203/V28은 제외한다. main과 후보의 Git 조상 관계가 같다고 표현하지 않는다. 원래 JAR·PropertiesLauncher·외부 라이브러리8개의 복구 증거는 격리 환경의 `overlay.json`으로 연결한다. 실제 운영 외부 설정 경로를 확인한 후 `/etc/yanus` 경로에서도 격리 복구125547ms와 승인 후보의 정상 배포를 추가 검증했다.

## 서버 준비와 전달

Node22+, JDK21, systemd, flock, psql, unzip, jq, timeout이 필요하다. 검토한 `ops/deploy/install-helper.sh`가 root 소유 도구를 `/usr/local/lib/yanus-deploy`에 설치하고 yanus 사용자에게 고정 helper 경로의 제한된 sudo만 부여한다. helper는 정확히 한 개의 `/home/yanus/incoming/<40자리 commit>` 인수만 받고 root 소유 baseline을 확인한다. 설치 스크립트는 후보를 활성화하거나 Actions를 켜지 않는다.

GitHub에는 기존 서버 secret과 별도로 `SERVER_KNOWN_HOSTS`가 필요하다. 기존 신뢰된 SSH 연결에서 읽은 서버 Ed25519 공개 키를 고정하고 StrictHostKeyChecking=yes를 사용한다. 호스트명 패턴은 전용 known_hosts 파일에서 서버 Secret의 연결 이름/포트를 수용하지만 허용 키는 그 서버 키 하나다. 배포 시 ssh-keyscan 결과를 그대로 신뢰하거나 host key 검사를 끄지 않는다. `production` environment와 배포 활성화 변수는 서버 준비 후 적용했다. 기존 yanus 계정의 다른 sudo 허용은 보존되므로 helper용 규칙을 전체 계정의 최소 권한이라고 표현하지 않는다.

운영 적용 JAR SHA256는 `50ec1c9c6f28f5cc389313cce2c8e038f01800deea24ed53dbf00245801b5066`, 실행 commit은 `4493049aebfe4b8dc0156aba0a777ff28359329d`이다. 2026-10-05T10:41:08Z 서비스 기동, 공개 API1303ms UP, 10:41:40.234119Z Slack SUCCESS 수신을 확인했다. 모두 단일 관찰이며 고객 장애 복구 시간이나 SLO를 의미하지 않는다.

## 교체와 복구

workflow concurrency와 서버 flock으로 겹치는 활성화를 막는다. GitHub 예약/큐의 실행 순서를 시간 보장으로 해석하지 않는다. 사용자 writable incoming 파일을 root 소유 릴리스에 복사한 뒤 digest/commit/schema를 다시 검사한다. 기존 프로세스와 HTTP도 정상이 아니면 교체하지 않는다.

JAR·기존 포인터·unit/drop-in·.env·외부 properties·overlay·baseline은 root0700 snapshot에 보관한다. 운영의 `/etc/yanus/application-observability.properties`와 기존 QA 경로 `/opt/yanus-observability/application-observability.properties`를 모두 지원하며 존재하는 regular 파일만 교체한다. 실패 시 실제 운영 경로도 원본으로 복구한다. 기존 일반 app.jar도 보존하며 같은 filesystem의 임시 symlink rename으로 새 릴리스를 활성화한다. candidate는 native `java -jar`를 사용하고 호환 overlay는 이전 snapshot에 보존한다. 다른 설정은 유지한다.

restart 명령은30초, private process/readiness/info/main 확인은 단조시계 기준120초, public 확인은60초로 제한한다. 실패하면 이전 포인터/설정을 복원하고 이전 hash와 HTTP를120초 안에 확인한다. legacy 이전 JAR는 build-info 대신 snapshot hash+설정+기존 health/main 계약을 확인한다.

| 결과 | 동작·job 상태 |
| --- | --- |
| SUCCESS | 실제 후보 commit·DB readiness·main/public 계약 확인. baseline 갱신, exit0 |
| PREFLIGHT_BLOCKED / DEPLOYMENT_LOCKED | 활성화 전 거절. 기존 프로세스 보존 |
| ROLLED_BACK | 이전 앱/설정 복구 확인. 후보 배포 job은 exit1 |
| ROLLBACK_FAILED | 자동 반복 중단, exit1. root snapshot의 이전 JAR·설정으로 수동 복구 후 확인 |

배포·복구 Slack 이벤트와 전달 상태는 고정 메시지·snapshot notification.json에 별도 남긴다. 웹훅 실패가 정상 배포를 되돌리지 않는다. 실제 webhook은 `/etc/yanus-deploy/slack-webhook`의 보호된 파일이며 값은 공개 기록에 넣지 않는다.

## 격리 검증 재현

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test bootJar
node --test ops/deploy/verify-release.test.mjs
bash -n ops/deploy/deploy.sh ops/deploy/install-helper.sh
docker build -t yanus-systemd-qa:205 ops/deploy/qa
docker run -d --name yanus-systemd-qa-205 --privileged --cgroupns=private --tmpfs /run --tmpfs /run/lock -v "$PWD:/source:ro" yanus-systemd-qa:205
docker exec yanus-systemd-qa-205 node /source/ops/deploy/qa/setup.mjs
docker exec yanus-systemd-qa-205 bash /usr/local/lib/yanus-deploy/deploy/deploy.sh /home/yanus/incoming/COMMIT_FROM_SETUP
docker exec yanus-systemd-qa-205 node /source/ops/deploy/qa/scenarios.mjs
docker cp yanus-systemd-qa-205:/source-evidence/scenarios.json build/deployment-scenarios.json
docker exec yanus-systemd-qa-205 node /source/ops/deploy/qa/legacy-scenario.mjs
docker cp yanus-systemd-qa-205:/source-evidence/legacy.json build/deployment-legacy.json
docker rm -f yanus-systemd-qa-205
```

폐기 가능한 로컬 systemd 컨테이너 전용이다. 호스트의 `/sys/fs/cgroup`를 추가 bind하면 PID 소속 판정이 꼬여 PostgreSQL unit이 starting에서 멈췄다. private cgroup namespace의 기본 mount를 사용한 뒤 실제 systemd·PostgreSQL·JAR가 정상 기동했다. 이는 QA 환경 구성 중 발견한 문제이며 PROD 장애 경험으로 기록하지 않는다.

readiness 통합 테스트는 실제 PostgreSQL을 중단해503·liveness200과 public 관리 경로403을 확인한다. 배포 HTTP 계약 테스트는 잘못된 commit·관리만 정상·DB 실패·HTML 응답을 거절한다. 실제 systemd 훈련 결과는 [운영 포트폴리오](portfolio-operations.md)에 실험 환경과 시각을 붙여 기록한다.

운영 baseline의 PropertiesLauncher 복원은 `overlay-scenario.mjs`로 따로 검증했다. 격리 컨테이너를 유지한 상태에서 읽기 복사한 기존 JAR를 `build/baseline/production.jar`, 기존 외부 라이브러리를 `build/baseline/lib`에 준비하고 `docker exec yanus-systemd-qa-205 node /source/ops/deploy/qa/overlay-scenario.mjs`를 실행한다. 해당 스크립트는 명시한 baseline hash와 QA 환경만 허용한다. 운영 `.env`/DB 데이터는 복사하지 않는다. `docker cp ...:/source-evidence/overlay.json`으로 증거를 build/ 밖에 보존한 후 컨테이너를 폐기한다.
