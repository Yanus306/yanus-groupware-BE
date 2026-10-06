# PostgreSQL 격리 복원 훈련

## 목적과 실행 경계

[#212](https://github.com/Yanus306/yanus-groupware-BE/issues/212)에서 생성하고 Mac에 검증 저장한 암호화 사본을 실제 PostgreSQL16과 백엔드에 연결한다. [#213](https://github.com/Yanus306/yanus-groupware-BE/issues/213)은 복원 가능한 데이터 시점, 데이터·권한 검증, 조회 API와 실패 후 정리를 확인한다.

도구는 **백업 디렉터리·백업 ID·로컬 개인키·boot JAR만** 입력받는다. 운영 호스트·DB URL·기존 컨테이너를 대상으로 지정하는 옵션은 없다. 새 이름의 Docker PostgreSQL을 `--network none`으로 생성하고 앱은 그 네트워크 공간의 loopback으로만 접속한다. 호스트 공개 포트와 운영 DB 접속은 없다. 데이터는 컨테이너 tmpfs에 둔다.

개인키는 Mac의 age 프로세스만 사용한다. Docker에 키를 전달하지 않는다. 복호화된 파일과 격리 앱 설정은 사용자0700 임시 디렉터리/파일0600에 보관하며 종료 시 제거한다. 보호된 원본 암호화 사본은 유지한다. 실제 조회 응답·직원 정보·SQL 결과·토큰은 보고서에 출력하지 않는다.

## 실행

Mac의 Docker Desktop·Node22+·age·Java21/Gradle로 빌드한 boot JAR가 필요하다. Docker가 다운로드할 이미지와 패키지는 훈련 데이터와 별개이며, 이미지 준비가 끝난 후 DB는 외부 네트워크 없이 동작한다. 처음 실행하면 이미지 준비 시간이 더 걸린다.

```bash
./gradlew bootJar
node ops/backup/restore-drill.mjs \
  "$HOME/Library/Application Support/YanusBackup/archives" \
  BACKUP_ID \
  "$HOME/Library/Application Support/YanusBackup/keys/identity.txt" \
  build/libs/attendance-0.0.1-SNAPSHOT.jar
```

`BACKUP_ID`는 Mac의 `last-success.json`에서 선택한다. 개인키 내용을 터미널·GitHub·Notion에 붙여넣지 않는다. 성공은 `status=SUCCESS`와 `cleanup`을 함께 확인한다. 실패는 exit1, 단계와 제한된 사유를 반환한다. `Ctrl-C`/SIGTERM에도 정리를 수행하며, 강제 SIGKILL·Docker 장애에서는 자동 정리가 보장되지 않는다.

## 검증 순서

| 단계 | 실제 확인 |
| --- | --- |
| 입력 | 암호화 크기·SHA256·ID·snapshot, 개인키 파일 종류·권한 |
| 복호화 | age 인증, 네 파일만 허용하는 tar 검사, 경로 조작·링크·중복·불완전 tar 거절, 내부 SHA256 |
| 역할·DB | 비밀번호가 제외된 역할 정의 복원, 원래 DB 소유자 지정, 오류 즉시 중단 |
| 데이터 | pg_restore 실제 실행, 같은 snapshot의 모든 public 업무 테이블 건수와 Flyway 전체 이력 비교 |
| 제약·권한·sequence | public 제약 정의·관계/sequence 소유자/ACL·DB 소유자 비교. dump의 SEQUENCE SET만 읽어 last_value·is_called를 복원 DB와 대조 |
| 앱 | Flyway 재실행 없이 Hibernate validate, 읽기 전용 훈련 역할 연결, readiness 정상 |
| API | 격리용 임시 JWT로 GET /api/v1/teams의 SUCCESS·건수 일치, 무인증401/403 |
| 쓰기 거절 | 읽기 전용 transaction 확인, DELETE의 실제 read-only/permission 오류 확인 |
| 정리 | 앱·DB·tmpfs 데이터·임시 평문/설정 제거. 실패하면 성공으로 표시하지 않음 |

원래 bootstrap 역할인 `postgres`를 다른 역할로 대체하면 일부 `GRANTED BY postgres` 역할 권한 복원이 실패하는 사례를 실제로 확인했다. 새 PostgreSQL의 bootstrap도 `postgres`로 두고, 검증된 작업 사본에서 이미 존재하는 `CREATE ROLE postgres;` 한 줄만 제외한다. ALTER/GRANT 오류는 무시하지 않는다. 원본 암호화 archive는 수정하지 않는다. PostgreSQL은 초기 superuser의 CREATE 충돌을 복원 시 주의할 사항으로 설명한다. [PostgreSQL16 pg_dumpall](https://www.postgresql.org/docs/16/app-pg-dumpall.html)

업무 scheduler 자체를 제거한 JAR가 아니다. 외부 네트워크 차단과 SELECT 전용 계정·read-only transaction으로 훈련 중 쓰기/외부 호출을 막는다. 격리 JWT는 운영 인증키나 사용자 로그인 검증의 증거가 아니다.

## 실패 진단과 재훈련

| 결과 | 조치 |
| --- | --- |
| ARCHIVE_CHECKSUM_MISMATCH | 원본을 덮어쓰지 말고 서버/Mac metadata·크기·hash 대조, 다른 유효 사본 선택 |
| DECRYPT_FAILED / UNPROTECTED_IDENTITY | 해당 사본의 개인키와0600 권한 확인. 키 분실은 암호화로 우회 복구할 수 없음 |
| UNSAFE_ARCHIVE_ENTRY / INTERNAL_CHECKSUM_MISMATCH | 변조·잘못된 포맷 여부 조사, 압축 해제 제한을 풀어서 실행하지 않음 |
| roles / createdb / restore 실패 | 보호된 진단 파일의 단계 확인, 누락 역할·DB major·dump 유효성 조사 |
| 데이터·제약·Flyway·ACL 불일치 | 복원 실패로 취급, 원본 snapshot과 코드/스키마 호환성 확인 |
| api 실패 | 해당 JAR와 백업 V27 호환성·readiness 확인. 운영 URL로 대신 검증하지 않음 |
| CLEANUP_FAILED | 표시된 자체 훈련 컨테이너를 조사하고 정리 완료 후 재훈련 |

Docker 명령 실패의 stderr는 백업 디렉터리의 `restore-diagnostic.json`(0600)에만 기록한다. 실제 SQL/식별자가 포함될 수 있어 공개하지 않는다. 성공한 이후에도 예상 쓰기 거절 진단이 남을 수 있으므로 이 파일 존재만으로 최근 훈련 실패를 판단하지 않는다. 최종 `status`를 우선한다.

자체 훈련 잔여물만 조사한다. 사용자 DB나 전체 Docker 자원을 일괄 정리하지 않는다.

```bash
docker ps -a --filter label=yanus.restore.drill
docker ps -a --filter label=yanus.restore.fixture
```

## 반복 검증과 CI

```bash
node --test ops/backup/*.test.mjs
node ops/backup/qa/restore-fixture.mjs build/libs/attendance-0.0.1-SNAPSHOT.jar
```

CI는 새 DB에 실제 백엔드의 현재 versioned Flyway inventory(관찰 시 V1~V27)를 적용하고 임시 팀 두 건을 만든 뒤, 같은 백업 스크립트로 암호화·복원한다. 기대하는 이력 개수도 현재 migration 파일 목록을 기준으로 계산한다. 운영 DB·SSH·고객 사본·운영 복호화키를 사용하지 않는다. 임시 키와 사본도 종료 시 삭제한다. 열린 키 권한·잘못된 키·암호화 손상·hash는 맞지만 유효하지 않은 dump·소유자 역할 누락·운영 대상 옵션·SIGTERM 정리를 검증한다. CI `test` job은 기존 테스트와 빌드가 통과한 같은 JAR로 실행한다.

로컬 실제 사본의 실패 주입은 다음 명령으로 수행하며 변경은 별도 임시 사본에만 적용한다.

```bash
node ops/backup/qa/restore-negative.mjs \
  "$HOME/Library/Application Support/YanusBackup/archives" \
  BACKUP_ID \
  "$HOME/Library/Application Support/YanusBackup/keys/identity.txt" \
  build/libs/attendance-0.0.1-SNAPSHOT.jar
```

## 실제 Mac 사본 복원 관찰 · 2026-10-05 KST

- 대상: `20261005T131138Z-65fb8f2213613b45`, snapshot **22:11:39 KST**, age **184552 bytes**.
- 실제 복원: public 테이블 **20개**, 성공 Flyway **27개**, 관계 소유권/ACL **38개**, 제약조건 **51개**, sequence 값/호출 상태 **18개** 대조 일치. 조회 API200·건수 일치, 무인증 거절, 훈련 계정 쓰기 거절.
- 재빌드 JAR SHA256: `71ae7244d713fae52a696524d85059153f44bcfbdd9dd7ae798ba695e28e850f`. 운영 소스 main `218f522`과 업무 Java/스키마는 동일하다. ZIP/빌드 메타데이터가 다른 운영 artifact와 같은 파일이라고 주장하지 않는다. #203/V28은 포함하지 않는다.
- 실제 사본 실패 시험 **6개 통과**. 추가 열린 개인키 권한 거절을 포함한 **7개**는 고객 데이터 없는 실제 CI fixture로 검증한다. 성공·실패·취소 후 자체 훈련 컨테이너와 평문 제거를 확인했다. 운영 DB 변경/중단 없이 수행했다.
- CI용 새 데이터 fixture에서도 실제 Flyway·백업·복원·조회와 실패7개를 로컬 실행해 통과했다. 이는 GitHub runner의 실행 결과와 별도로 기록한다.

sequence 검증까지 포함한 최종 실제 사본 실행의 데이터 경과는 시작 시 **2999초(49분59초)**였다. 당시 선택한 snapshot으로부터의 경과이며 시스템이 보장한 RPO가 아니다. 해당 실행 중 다른 격리 QA도 실행 중이었다.

| 단계 | 단일 실행 관찰 |
| --- | --- |
| 입력 크기·hash 검사 | 3ms |
| 복호화·압축/내부 hash 검사 | 16ms |
| 이미지 확인·DB/역할 준비 | 3635ms |
| pg_restore | 107ms |
| DB 데이터·권한·sequence·쓰기 거절 검증 | 2916ms |
| 앱 준비·조회 API·인증 거절 | 4971ms |
| 전체 정리 전 | 11698ms |
| 정리 | 118ms |

복원 실패·재검증의 Slack 전달은 실제 격리 실패/후속 성공을 확인한 뒤 `[검증용]` 안내 알림으로 별도 시험한다. 실제 고객 장애나 복원 시간 알림 자동화의 증거로 사용하지 않는다.

```bash
node ops/backup/verify-slack.mjs fire PRIVATE_RECEIPT_FILE restore
node ops/backup/verify-slack.mjs resolve PRIVATE_RECEIPT_FILE restore
```

실제 검증용 한국어 [장애22:58:10KST](https://yanushq.slack.com/archives/C0C6N4CJHRC/p1791208690773889)·[복구23:03:10KST](https://yanushq.slack.com/archives/C0C6N4CJHRC/p1791208990792709) 수신 확인. Alertmanager 직접 입력 전달 시험이며 실제 DB 장애 감지·복원 자동 알림·MTTD/MTTR 측정값이 아니다.

단계별 시간과 해당 실행 시작 시 데이터 경과는 JSON 결과로 남긴다. Mac에 이미 있는 사본을 사용하므로 **SSH 다운로드 시간을 포함하지 않는다**. 이미지가 준비된 소규모 DB 단일 관찰값은 보장 RTO·장기 평균이 아니다. 하루1회 백업과 Mac 잠자기/오프라인의 전달 지연 때문에 고정 RPO도 보장하지 않는다. 복구 가능한 시점은 ACK 시각이 아닌 **snapshot 시각**이다.

도구의 현재 범위는 PostgreSQL16, public 스키마, 복호화 tar128MiB·metadata 파일당4MiB 이하이다. DB가 커지거나 다른 schema/extension을 사용하면 용량·권한·검증 범위를 먼저 확장한다. WAL/PITR·첨부 파일·서버 전체 설정·운영 비밀번호·운영 서버 교체는 이 훈련 범위에 포함되지 않는다. Mac과 유일한 개인키를 동시에 잃는 문제는 별도 키 복구 매체가 필요하다.
