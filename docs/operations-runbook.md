# yANUs 운영 대응 절차

먼저 장애 시각(KST/UTC), 영향 API, requestId, 변경·배포 시각을 기록한다. 보드의 동일 시간 범위에서 조사하고 조치 후 실제 API와 같은 지표로 복구를 확인한다. 로그·캡처를 공유할 때 토큰·계정·본문을 제거한다. 알림은 조건의 충족이며 원인을 확정하는 메시지가 아니다.

| 증상 | 첫 화면·쿼리 | 읽기 전용 조사 | 원인 분리·조치 | 복구 확인·중단 조건 |
| --- | --- | --- | --- | --- |
| API 5xx | 02 → 03, 5xx 비율·동일 requestId | `ssh app-server 'sudo journalctl -u yanus --since "30 minutes ago" --no-pager'` | 예외 유형·DB·SMTP/MinIO 실패와 배포 시각 비교. 확인된 설정이나 실패 버전을 복구 | 동일 API 응답·5xx 감소 확인. 데이터 오류·반복 재시작이면 담당자에게 전달 |
| P95 상승 | 02 → 05 → 04, GC·Hikari Pending·CPU/iowait | `ssh app-server 'vmstat 1 5'` | 트래픽 증가/GC/풀 대기/쿼리·I/O 구분. 느린 트랜잭션·외부 호출부터 조사 | 같은 요청 조건에서 P95·대기 확인. 근거 없이 풀/메모리 확대하지 않음 |
| DB DOWN·Deadlock | 05, `pg_up`, connections | `ssh data-server 'sudo systemctl status postgresql --no-pager'` | DB 프로세스와 exporter 인증/socket 분리. `pg_stat_activity`·잠금은 비공개 화면에서 조회 | DB health·읽기 API 확인. DB 중단·복원은 승인된 절차로 진행 |
| 메모리 부족 | 04·02, available·swap·GC | `ssh app-server 'free -m'` | OS cache와 실제 압박, JVM heap과 native memory, cgroup 제한 구분 | available·PSI·OOM 이벤트 확인. 반복 OOM이면 서비스 소유자에게 전달 |
| 디스크 85/95% | 04·05, 루트 여유 용량 | `ssh monitoring-server 'df -h; sudo du -xhd1 /var/lib /var/log'` | TSDB/Loki/로그/DB 증가 구분. 보존 정책·백업 확인 후 해당 소유자의 절차 사용 | 여유 용량·증가율 재확인. DB/WAL/TSDB 임의 삭제 금지 |
| scrape DOWN | 01, `up{environment="prod"}` | `ssh monitoring-server 'curl -fsS --max-time 10 http://127.0.0.1:9090/api/v1/targets'` | 실제 앱 중단과 exporter·9092 proxy·ACL 장애 분리 | 해당 target UP 및 실제 API 확인. 관찰 연결 복구를 업무 복구로 단정하지 않음 |
| 공개 API DOWN | 외부 Actions 결과 → 01 | `ssh app-server 'curl -fsS --max-time 10 http://127.0.0.1:8080/v3/api-docs'` | local 정상/public 실패면 DNS·회선·Cloudflare·Nginx 조사. 200 HTML은 JSON 성공 아님 | 외부 HTTPS 계약 재확인. origin 보안/인증 우회로 해결하지 않음 |
| Grafana DOWN | 외부 점검 → monitoring-server | `ssh monitoring-server 'systemctl is-active grafana-server; curl -fsS --max-time 10 http://127.0.0.1:3000/api/health'` | Grafana DB·프로세스, app Nginx upstream, Cloudflare·DNS 구분 | 공개 로그인·보드 조회 확인. API 장애와 별개로 기록 |
| Alertmanager DOWN·Slack 없음 | 01·Prometheus alerts, `up{job="yanus-alertmanager"}` | `ssh monitoring-server 'systemctl is-active alertmanager; sudo journalctl -u alertmanager -n 50 --no-pager'` | pending/for·PROD labels·group_wait·inhibition·Slack 권한/429 구분. webhook 값은 출력하지 않음 | 승인된 synthetic 알림/복구 수신 확인. 전체 모니터링 중단이면 외부 점검 확인 |
| 외부 점검 실행 공백 | GitHub uptime workflow 최근 실행 | `gh run list --repo Yanus306/yanus-groupware-BE --workflow uptime.yml --limit 5` | default branch 적용·schedule 활성화·60일 비활성화·Actions 지연 구분 | 다음 실제 run ID 기록. 반복 공백이면 별도 외부 감시 검토 |
| 배포·롤백 실패 | 배포 job 결과 → 01·03 | `ssh app-server 'systemctl is-active yanus; sudo journalctl -u yanus -n 100 --no-pager'` | 후보 hash·commit·readiness·main/public HTTP·실행 설정·DB schema 확인. #205 구현/검증 전 자동 복구가 있다고 가정하지 않음 | 이전 버전·설정과 API 확인. ROLLBACK_FAILED이면 자동 재시도를 멈추고 보존 snapshot으로 수동 복구 |

## 로그가 비어 있을 때

03에서 host·시간 범위·requestId를 확인한다. app-server의 파일 존재/권한, Alloy service, Loki health, push 인증, metadata timer 순서로 조사한다. 무요청과 수집 장애를 구분한다. root에서 읽힌다고 Alloy가 읽을 수 있다고 판단하지 않는다.

## 기록 양식

발생/감지/알림 도착/조치 시작/정상 확인의 UTC 시각, 영향, 관찰 지표·안전한 로그, 원인, 복구 명령과 결과, 후속 조치를 남긴다. 격리 실험인지 실제 장애인지 명시한다. 한 번의 실험을 장기 평균 MTTD/MTTR로 표시하지 않는다.

2026-10-05: monitoring-server의 Alertmanager 0.28.1·Prometheus 전달·10개 규칙·5개 보드 도움말을 적용했다. yANUs/yanus-서버-알람에서 승인된 검증용 알림의 FIRING(09:33:49 UTC)·RESOLVED(09:35:29 UTC)를 확인했다. 실제 서비스 장애 실험은 아니다. GitHub Secret은 등록했으나 기본 브랜치 uptime workflow 활성화·실제 예약 run은 남아 있다. 전체 앱/DB 배포는 수행하지 않았다.
