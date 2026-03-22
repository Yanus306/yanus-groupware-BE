# Yanus Groupware — Backend

> TDD + DDD 기반으로 설계한 그룹웨어 백엔드 프로젝트

---

## 프로젝트 소개

동아리/팀 단위로 사용할 수 있는 그룹웨어 백엔드 서버입니다.
출퇴근 관리, 회원 관리, 휴가 신청 등 조직 운영에 필요한 기능을 제공합니다.

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
| CI | GitHub Actions |
| Container | Docker, Docker Compose |

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
│   ├── config/          # Security, QueryDSL, Swagger
│   ├── exception/       # BusinessException, ErrorCode, GlobalExceptionHandler
│   └── response/        # ApiResponse
├── team/
├── auth/
├── member/
└── attendance/
    ├── domain/           # Attendance, AttendanceStatus, AttendanceRepository
    ├── application/      # AttendanceService
    ├── infrastructure/   # AttendanceJpaRepository
    └── presentation/     # AttendanceController, dto/
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
