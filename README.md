# Yanus Groupware — Backend

> TDD + DDD 기반으로 설계한 그룹웨어 백엔드 프로젝트

---

## 프로젝트 소개

동아리/팀 단위로 사용할 수 있는 그룹웨어 백엔드 서버입니다.
출퇴근 관리, 회원 관리, 휴가 신청, 업무 관리, 캘린더 등 조직 운영에 필요한 기능을 제공합니다.

---

## 구현 현황

| 도메인 | 기능 | 상태 |
|--------|------|------|
| **Team** | 팀 초기 데이터 구성 | ✅ 완료 |
| **Auth** | 이메일 로그인, JWT 발급/재발급/로그아웃, 내 정보 조회 | ✅ 완료 |
| **Member** | 회원 목록/상세 조회, 역할 변경, 활성화/비활성화, 프로필 수정 | ✅ 완료 |
| **Attendance** | 출근/퇴근 체크인, 내 기록 조회, 관리자 조회(팀/날짜 필터), 자정 자동 퇴근, 개인 근무 일정 설정 | ✅ 완료 |
| **Leave** | 휴가 신청, 본인/팀 목록 조회, 승인/반려 | ✅ 완료 |
| **Task** | 개인/팀 Task 생성, 완료 토글, 수정/삭제, 목록 조회(날짜 범위 필터) | ✅ 완료 |
| **Calendar** | 이벤트 생성/수정/삭제, 날짜 범위 조회, 생성자 기준 조회 | ✅ 완료 |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.11 |
| ORM | Spring Data JPA, Hibernate 6 |
| Query | QueryDSL 5.1.0 |
| DB | PostgreSQL 15 |
| Migration | Flyway 11 |
| Auth | JWT (jjwt 0.12.6), Spring Security 6 |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Test | JUnit5, AssertJ, Mockito, Testcontainers |
| Build | Gradle |
| CI/CD | GitHub Actions |
| Container | Docker, Docker Compose |
| Infra | 온프레미스 서버 (Nginx 리버스 프록시, systemd) |

---

## 아키텍처

### DDD (Domain Driven Design)

```
presentation → application → domain ← infrastructure
```

- `domain` : 엔티티, Repository 인터페이스, 도메인 서비스 (순수 Java)
- `application` : 유스케이스, 트랜잭션 경계
- `infrastructure` : Spring Data JPA 구현체, QueryDSL
- `presentation` : Controller, DTO (record)

### 패키지 구조

```
com.yanus.attendance
├── global/
│   ├── config/          # Security, QueryDSL, Swagger, Scheduling
│   ├── exception/       # BusinessException, ErrorCode, GlobalExceptionHandler
│   └── response/        # ApiResponse
├── team/
├── auth/
├── member/
├── attendance/
│   ├── domain/           # Attendance, WorkSchedule, Repository 인터페이스
│   ├── application/      # AttendanceService, WorkScheduleService, AttendanceScheduler
│   ├── infrastructure/   # JPA 구현체, QueryDSL
│   └── presentation/     # Controller, dto/
├── leave/
│   ├── domain/           # LeaveRequest, LeaveCategory, LeaveStatus
│   ├── application/      # LeaveService
│   ├── infrastructure/   # JPA 구현체
│   └── presentation/     # Controller, dto/
├── task/
│   ├── domain/           # Task, TaskPriority, Repository 인터페이스
│   ├── application/      # TaskService
│   ├── infrastructure/   # JPA 구현체, QueryDSL
│   └── presentation/     # Controller, dto/
└── calendar/
    ├── domain/           # CalendarEvent, Repository 인터페이스
    ├── application/      # CalendarEventService
    ├── infrastructure/   # JPA 구현체
    └── presentation/     # Controller, dto/
```

---

## 개발 방법론

### TDD — Red → Green → Refactor

- 테스트 먼저 작성 후 실패 확인 → 기능 구현 → 통과 확인 → 리팩터
- 최소 단위로 커밋 (`test:` → `feat:` → `refactor:`)
- Given / When / Then 구조로 테스트 작성

### 테스트 전략

| 레이어 | 방식 | 설명 |
|--------|------|------|
| 도메인 단위 | JUnit5 + AssertJ | 엔티티 내부 로직 |
| 서비스 단위 | Fake Repository 패턴 | DB 없이 빠른 테스트 |
| 통합 | Testcontainers | 실제 DB 연동 |

**Fake Repository 패턴** — Mockito 대신 인메모리 HashMap으로 구현하여 실제 동작과 가깝게 테스트

```java
public class FakeAttendanceRepository implements AttendanceRepository {
    private final Map<Long, Attendance> store = new HashMap<>();
    // ...
}
```

---

## 로컬 실행

### 사전 준비
- Java 21
- Docker

### 환경변수 설정 (`.env`)

```env
DB_URL=jdbc:postgresql://localhost:5432/yanus
DB_USERNAME=yanus
DB_PASSWORD=your_password
JWT_SECRET=your-secret-key-must-be-at-least-32-characters
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000
```

### 실행

```bash
# DB 실행
docker compose up -d

# 서버 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Swagger UI
```
http://localhost:8080/swagger-ui/index.html
```

---

## 트러블슈팅 기록

개발 중 발생한 이슈와 해결 과정을 문서화하고 있습니다.

- [트러블슈팅 기록](docs/troubleshooting.md)
