# yANUs Groupware — Backend

> 동아리 그룹웨어 백엔드 서버

[![CI](https://github.com/Yanus306/yanus-groupware-BE/actions/workflows/ci.yml/badge.svg)](https://github.com/Yanus306/yanus-groupware-BE/actions/workflows/ci.yml)

🌐 **서비스**: [yanus.bond](https://yanus.bond)

---

## 프로젝트 소개

기존에 사용하던 출퇴근 관리 웹 서비스는 출근과 퇴근 기록만 제공했습니다.  
지각 여부 판단과 지각비 정산은 매달 관리자가 직접 기록을 보고 수기로 처리해야 했습니다.

이 프로젝트는 그 문제를 해결하는 것에서 출발했습니다.  
개인별 근무 일정을 등록하면, 실제 출근 시간과 비교해 지각 여부를 자동으로 판단하고 월별 지각 내역을 정산합니다.  
출퇴근 외에도 팀 운영에 필요한 휴가 관리, 업무 관리, 캘린더, 파일 공유 기능을 함께 제공합니다.

**프론트엔드 레포**: [yanus-groupware](https://github.com/Yanus306/yanus-groupware)

---

## 주요 기능

| 도메인 | 기능 |
| --- | --- |
| **Auth** | 이메일 로그인, JWT 발급/재발급/로그아웃, 내 정보 조회 |
| **Member** | 회원 목록/상세 조회, 역할 변경, 활성화/비활성화, 프로필 수정 |
| **Attendance** | 출근/퇴근 체크인 (IP 검증), 개인 근무 일정 설정, 날짜별 일정 오버라이드, 월별 지각 정산 |
| **Leave** | 휴가 신청, 승인/반려, 본인·팀 목록 조회 |
| **Task** | 개인/팀 Task CRUD, 다중 멤버 배정, 날짜 범위 필터 |
| **Calendar** | 이벤트 CRUD, 날짜 범위 조회, 생성자 기준 조회 |

---

## 기술 스택

| 분류 | 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3 |
| ORM | Spring Data JPA, Hibernate 6 |
| Query | QueryDSL 5.1.0 |
| DB | PostgreSQL 15 |
| Migration | Flyway 11 |
| Auth | JWT (jjwt 0.12.6), Spring Security 6 |
| Storage | MinIO (S3 호환 객체 스토리지) |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Test | JUnit5, AssertJ, Testcontainers |
| Build | Gradle |
| CI/CD | GitHub Actions |
| Infra | 온프레미스 (Nginx, systemd, Cloudflare) |

---

## 아키텍처

### 인프라 구성

```
외부 요청
  → Cloudflare (DNS, DDoS 방어)
  → 공유기 포트포워딩 (80, 443)
  → Nginx 리버스 프록시
  → Spring Boot :8080

  app-server                  data-server
  ├── Nginx                   ├── PostgreSQL 15
  ├── Spring Boot JAR         └── MinIO
  └── GitHub Actions Runner
```

앱 서버와 데이터 서버를 VM 2대로 분리했습니다.  
앱 배포 시 데이터 서버에 영향이 없고, 장애 발생 시 원인 분리가 쉽습니다.

### 애플리케이션 레이어

```
presentation → application → domain ← infrastructure
```

### CD 파이프라인

```
main 브랜치 push
  → GitHub Actions 트리거
  → 테스트 + 빌드
  → SCP로 JAR 업로드
  → SSH로 systemctl restart
```

---

## 트러블슈팅 / 개선 사항

### 1. CI 통과 → 운영 배포 후 502 (Flyway FK 제약 위반)

`V12__remove_default_teams.sql`에서 `DELETE FROM team`을 실행했을 때 CI는 통과했지만 운영 배포 후 502가 발생했습니다.  
CI 환경에는 멤버 데이터가 없어 FK 제약이 걸리지 않았고, 실제 데이터가 있는 운영에서만 터졌습니다.

```sql
-- Before
DELETE FROM team;

-- After: 참조 중인 팀은 제외
DELETE FROM team
WHERE team_id NOT IN (
    SELECT DISTINCT team_id FROM member WHERE team_id IS NOT NULL
);
```

CI 데이터셋이 운영 데이터를 대표하지 못하는 경우, 마이그레이션 SQL은 데이터 존재 여부를 항상 가정하고 작성해야 한다는 것을 배웠습니다.

---

### 2. MinIO 의존성 충돌 (Spring Boot 3)

MinIO 8.5.x가 내부적으로 사용하는 `okhttp3`, `guava` 버전이 Spring Boot 3 관리 버전과 충돌했습니다.  
충돌 의존성을 명시적으로 제외하고 호환 버전을 직접 지정해서 해결했습니다.

```groovy
implementation('io.minio:minio:8.5.7') {
    exclude group: 'com.google.guava', module: 'guava'
    exclude group: 'com.squareup.okhttp3', module: 'okhttp'
}
implementation 'com.google.guava:guava:32.1.3-jre'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

---

### 3. 비활성화 멤버 로그인 차단 누락

관리자가 멤버를 `INACTIVE` 처리해도 로그인이 가능한 버그가 있었습니다.  
비밀번호 검증만 하고 상태 체크 로직이 없었기 때문입니다.

```java
if (member.isInactive()) {
    throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
}
```

---

### 4. GitHub Actions SSH 타임아웃

배포 시 `dial tcp <SERVER-IP>:22: i/o timeout`이 반복됐습니다.  
서버 내부 `sshd`는 정상이었지만 공유기/망 환경에서 외부 22번 포트가 차단된 상태였습니다.  
공유기에서 외부 `2222` → 내부 `22`로 포트포워딩하고 GitHub Secrets의 `SERVER_PORT`를 수정해서 해결했습니다.

---

전체 트러블슈팅 기록 → [docs/troubleshooting.md](docs/troubleshooting.md)

---

## 로컬 실행

```bash
# 사전 준비: Java 21, Docker

# DB + MinIO 실행
docker compose up -d

# 서버 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# Swagger UI
open http://localhost:8080/swagger-ui/index.html
```
