# PostgreSQL 백업과 Mac 사본

## 운영 구성

data-server의 PostgreSQL16 업무 DB를 매일 **03:20 Asia/Seoul**에 백업한다. systemd timer는 최대 2분 지연을 분산하며 서버가 꺼졌던 경우 Persistent로 다음 활성화 시 보완 실행한다. Mac의 사용자 LaunchAgent는 로그인 시와 실행 중 매시간 SSH로 암호화 파일을 가져온다. Mac 잠자기·종료·오프라인에는 전달이 지연되므로 상시 외부 백업 전달이나 고정 RPO를 보장하지 않는다.

R2는 카드 등록이 필요해 사용하지 않는다. 사용자가 **Mac 사본만 유지**를 선택했으므로 구글 드라이브 연결·유료 요금제 신청도 하지 않는다. 기존 SSH 신뢰 키와 접속 설정을 사용하며 새 공개 포트를 열지 않는다.

| 위치 | 내용 | 권한 |
| --- | --- | --- |
| 서버 `/var/backups/yanus-db` | age 암호화 archive·checksum metadata·Mac ACK | root 0700 / 파일0600 |
| 서버 `/run/yanus-backup` | 실행 중 dump·역할·검증 임시 평문, 종료 시 삭제 | root 0700·tmpfs |
| 서버 `/var/lib/yanus-backup` | 최근 생성·전송 확인 상태 | root 0700 |
| Mac `~/Library/Application Support/YanusBackup/archives` | 암호화 사본·검증 상태 | 사용자0700 / 파일0600 |
| Mac `~/Library/Application Support/YanusBackup/keys/identity.txt` | 복호화 개인키 | 사용자0600 |

개인키는 Mac에만 두고 서버에는 공개 recipient만 설치한다. **Mac과 키를 모두 잃으면 서버 archive도 복원할 수 없다.** 별도 보호된 키 복구 매체는 아직 확보하지 않았다. 암호화 파일·키·실제 데이터·접속 비밀번호를 GitHub나 Notion에 올리지 않는다.

## 백업에 포함되는 것

1. PostgreSQL repeatable-read **read-only transaction의 exported snapshot**으로 pg_dump(custom format)와 업무 테이블 검증 건수·Flyway 이력·제약·소유권/ACL을 읽는다.
2. 역할 정의는 `pg_dumpall --roles-only --no-role-passwords`로 수집한다. 로그인 비밀번호와 해시는 포함하지 않는다. 역할 정의는 클러스터 전역 정보이며 같은 DB snapshot에 묶인 것으로 주장하지 않는다. 역할/권한 변경 작업 중에는 별도 변경 관리가 필요하다.
3. dump·역할·검증 JSON의 SHA256을 manifest에 넣고 네 파일을 tar로 묶어 age로 암호화한다. 실패 시 이전 성공 시점은 보존하고 실패 단계를 기록한다.
4. Mac은 암호화 파일 크기·SHA256을 검증하고 파일 fsync·원자적 rename으로 저장한 뒤 ACK한다. SSH가 끊기면 ACK하지 않는다. ACK만 실패한 경우 유효한 Mac 파일을 다시 검사하여 재시도한다.

서비스 계정/OS 설정·첨부 파일·WAL은 이 논리 백업 범위에 포함되지 않는다. 시점 복구(PITR) 기능이 아니다. 복원 시 로그인 비밀번호는 격리 환경용으로 새로 설정해야 한다.

## 설치와 실행

서버는 Ubuntu의 Bash4+·PostgreSQL16 client·age·jq·util-linux가 필요하다. Linux 전용 backup.sh를 macOS 기본 Bash3.2에서 실행하지 않는다. 서버 설치는 root로 실행한다.

```bash
bash ops/backup/install-server.sh AGE_PUBLIC_RECIPIENT
systemctl list-timers --all yanus-db-backup.timer
systemctl start yanus-db-backup.service
journalctl -u yanus-db-backup.service --since '1 day ago' --no-pager
```

Mac은 Node22+, age, 기존 `data-server` SSH alias가 필요하다. 설치 CLI는 현재 Node 실행 파일의 절대 경로를 LaunchAgent에 저장하므로 Node 경로를 바꾼 경우 다시 설치한다.

```bash
node ops/backup/install-mac.mjs
launchctl print "gui/$(id -u)/kr.bond.yanus.db-backup.collector"
launchctl kickstart "gui/$(id -u)/kr.bond.yanus.db-backup.collector"
node ops/backup/collect.mjs "$HOME/Library/Application Support/YanusBackup/archives" data-server
```

root helper는 list/archive/ack 고정 명령과 백업 ID 형식·checksum을 검사한다. 기존 root SSH 계정의 전체 권한을 줄이는 별도 권한 경계는 아니다.

## 보존과 점검

14일을 기준으로 서버는 **Mac ACK가 있는 오래된 사본만** 정리한다. 최신 서버 백업과 미전송 백업은 삭제하지 않는다. Mac도 최신 유효 사본을 남긴다. 따라서 14일은 파일 수/디스크 용량의 강제 상한이 아니다. 오프라인이 길어져 서버 미전송 파일이 쌓이면 기존 디스크 경고와 함께 조사한다. 오래된 사본 재수집은 새로운 snapshot 시점으로 기록하지 않는다.

Mac의 `collection-status.json`은 마지막 수집 시도, `last-success.json`은 최신 전송 확인 snapshot이다. 서버 `offsite.json`의 ACK는 **그 시점의 Mac 저장 확인**이며 이후 Mac 파일 삭제·장치 고장을 보장하지 않는다. 정기 격리 복원과 Mac 사본/키 상태 점검이 필요하다.

## Grafana·Slack 대응

05 PostgreSQL의 **DB 백업 · Mac 사본**에서 최근 실행 성공, 서버 snapshot, Mac snapshot, 크기와 경과를 본다. 지표는 기존 내부 node exporter textfile로 수집하며 PostgreSQL이나 앱을 재시작하지 않는다.

| 경고 | 기준 | 대응 |
| --- | --- | --- |
| 최근 백업 실패 | 실패 상태 5분 | service journal → 실패 단계 → 디스크/DB/암호화 확인 → 이전 유효 백업 보존 후 재실행 |
| 서버 백업 지연 | snapshot 26시간 초과 5분 | timer 활성화·최근 실행·서버 시간·용량 확인 |
| Mac 사본 지연 | snapshot 30시간 초과 10분 | 새 서버 백업 존재 → Mac 로그인/잠자기/네트워크 → SSH·collector 상태 확인 |
| 지표 없음/정체 | 지표 누락 또는 갱신26시간 초과 5분 | exporter UP·textfile 경로/권한·timer 점검 |

기존 Alertmanager 묶음·반복 억제·한국어 장애/복구와 Slack `yanus-서버-알람`을 사용한다. 임계치는 운영 점검 경고이며 보장된 RPO/RTO가 아니다. 알림 전달 시험은 검증용으로 표시하며 실제 DB 장애를 유도하지 않는다.

## 검증 경계

collector unit test는 손상 파일·전송/ACK 실패·중복 실행·잔여 잠금·오래된 시점을 확인한다. Docker PG16 QA는 실제 dump·다중 Flyway 이력·age 복호화·동시 실행 거절·DB 접근 실패·평문 정리·보존·손상 ACK·경로 조작을 확인한다. fixture 검증과 실제 운영 사본을 구분한다.

```bash
node --test ops/backup/*.test.mjs
node ops/backup/qa/scenarios.mjs
docker run --rm --entrypoint promtool -v "$PWD/ops/backup:/work:ro" -w /work prom/prometheus:v2.45.3 test rules yanus-backup-rules.test.yml
```

실제 설치 관찰: 2026-10-05 KST, 첫 일회 systemd 예약-trigger에서 Flyway JSON 다중 줄 처리를 수정한 뒤 재예약으로 생성 성공. snapshot **21:58:42 KST**, 암호화 **184552 bytes**, Mac ACK **22:00:57 KST**. 생성 소요 **1초**는 단일 관찰값이다. 매일03:20 정규 예약과 hourly 수집의 첫 주기 실행은 아직 관찰하지 않았다. 실제 사본 복원 완료는 별도 [#213](https://github.com/Yanus306/yanus-groupware-BE/issues/213)에서 기록한다.

검증용 Slack 전달: [장애 수신22:02:52 KST](https://yanushq.slack.com/archives/C0C6N4CJHRC/p1791205372533409), [복구 수신22:07:52 KST](https://yanushq.slack.com/archives/C0C6N4CJHRC/p1791205672560549). Alertmanager 직접 입력 시험이며 실제 DB 백업 장애의 감지 시간을 측정한 것이 아니다. 기존 5분 group_interval을 유지했다.
