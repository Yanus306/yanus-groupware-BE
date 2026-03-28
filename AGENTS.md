# AGENTS.md — Yanus Groupware 개발 지침

## 역할
- 코드는 사용자가 직접 작성한다
- Codex는 가이드, 코드 리뷰, 방향 제시만 담당한다
- 파일 생성/수정은 명시적으로 요청받은 경우에만 한다

---

## 개발 방법론

### TDD (Test Driven Development)
- **Red → Green → Refactor** 사이클을 엄격히 지킨다
- 테스트 먼저 작성 → 실패 확인 → 기능 구현 → 통과 확인 → 리팩터
- 테스트 하나 작성 후 커밋, 기능 구현 후 커밋 (최소 단위)
- 테스트는 Given / When / Then 구조로 작성한다

```
// given
...
// when
...
// then
...
```

#### 테스트 레이어
| 레이어 | 도구 | 범위 |
|--------|------|------|
| 도메인 단위 테스트 | JUnit5, AssertJ | Entity, VO, Domain Service |
| 서비스 단위 테스트 | JUnit5, Mockito | Application Service |
| 통합 테스트 | @SpringBootTest, Testcontainers | Repository, Controller |
| 슬라이스 테스트 | @WebMvcTest | Controller |

#### 테스트 네이밍
```java
// 한글로 의도를 명확히 표현
@Test
void 이메일이_중복되면_예외를_던진다() {}

@Test
void 출근_체크인_성공_시_WORKING_상태로_저장된다() {}
```

---

### DDD (Domain Driven Design)

#### 패키지 구조
```
com.yanus.attendance
├── global/
│   ├── config/
│   ├── exception/
│   └── response/
└── {domain}/                      # team, auth, member, attendance, leave, task, calendar, chat, drive
    ├── domain/
    │   ├── {Entity}.java          # 엔티티 (순수 Java, JPA 어노테이션 허용)
    │   ├── {Repository}.java      # Repository 인터페이스 (포트)
    │   └── {DomainService}.java   # 도메인 서비스 (필요 시)
    ├── application/
    │   └── {Service}.java         # 유스케이스, 트랜잭션 경계
    ├── infrastructure/
    │   ├── {JpaRepository}.java   # Spring Data JPA
    │   └── {QueryRepository}.java # QueryDSL
    └── presentation/
        ├── {Controller}.java
        ├── dto/
        │   ├── {Request}.java     # record 사용 권장
        │   └── {Response}.java    # record 사용 권장
        └── {ControllerTest}.java  # @WebMvcTest
```

#### 레이어 의존 방향
```
presentation → application → domain ← infrastructure
```
- domain은 어떤 레이어도 의존하지 않는다
- infrastructure가 domain의 인터페이스를 구현한다

#### 엔티티 설계 원칙
- 엔티티는 상태 변경 메서드를 직접 갖는다 (setter 금지)
- 비즈니스 규칙은 엔티티 안에 캡슐화한다
- 생성자 대신 정적 팩토리 메서드 사용 권장

```java
// Bad
member.setStatus(MemberStatus.INACTIVE);

// Good
member.deactivate();
```

---

## 객체지향 생활 체조 원칙 (Object Calisthenics)

1. **한 메서드에 들여쓰기 1단계만** — 복잡한 로직은 메서드 추출
2. **else 예약어 금지** — early return 또는 전략 패턴으로 대체
3. **모든 원시값과 문자열 포장** — VO(Value Object) 적극 활용 (필요 시)
4. **한 줄에 점 하나만** — 디미터 법칙 준수
5. **축약 금지** — 클래스/변수명은 의도가 명확하게
6. **작은 클래스** — 50줄 이하 지향 (트레이드오프 허용)
7. **인스턴스 변수 최소화** — 2개 이하 지향 (엔티티 제외, 트레이드오프 허용)

> 트레이드오프: 엔티티, DTO 등 도메인 모델은 원칙보다 가독성 우선

---

## SOLID 원칙

| 원칙 | 적용 |
|------|------|
| **SRP** 단일 책임 | Service는 하나의 도메인만 담당, Controller는 HTTP만 담당 |
| **OCP** 개방-폐쇄 | 전략 패턴, 인터페이스 확장으로 기존 코드 수정 최소화 |
| **LSP** 리스코프 치환 | 상속보다 인터페이스/조합 우선 |
| **ISP** 인터페이스 분리 | Repository 인터페이스는 필요한 메서드만 선언 |
| **DIP** 의존 역전 | Application → Domain Interface ← Infrastructure 구현체 |

---

## 커밋 컨벤션

```
test: 이메일 로그인 실패 시 예외 테스트 작성
feat: 이메일 + 비밀번호 로그인 기능 구현
refactor: AuthService 메서드 추출 리팩터
```

| 타입 | 설명 |
|------|------|
| `test` | 테스트 코드 작성 (Red 단계) |
| `feat` | 기능 구현 (Green 단계) |
| `refactor` | 리팩터링 (Refactor 단계) |
| `fix` | 버그 수정 |
| `chore` | 설정, 의존성, 환경 |
| `docs` | 문서 |

---

## 브랜치 전략

```
main
└── feat/{domain}      # feat/auth, feat/member, feat/attendance ...
```

- global은 main에서 직접 작업
- 도메인별 브랜치에서 개발 후 main에 merge
- PR 없이 로컬 merge 가능 (개인 프로젝트)

---

## 코드 스타일

- **record** 사용: DTO (Request/Response), 불변 객체
- **@Getter + @NoArgsConstructor(access = PROTECTED)**: 엔티티 기본
- **정적 팩토리 메서드**: `of()`, `create()`, `from()`
- **Optional 반환**: Repository 단건 조회
- **void 반환 지양**: 의미 있는 값 반환 권장
- **@Transactional**: Service 메서드 단위로 적용, 읽기는 `readOnly = true`

---

## 예외 처리

- 비즈니스 예외는 반드시 `BusinessException(ErrorCode)` 사용
- ErrorCode에 도메인별 에러 코드 추가하며 관리
- Controller에서 예외를 catch하지 않는다 — GlobalExceptionHandler에 위임

---

## 트러블슈팅 문서 관리

- 개발 중 발생한 이슈는 반드시 `docs/troubleshooting.md` 에 기록한다
- 기술/도메인 단위로 섹션을 나눠서 하나의 파일에 관리한다

### 작성 형식

```markdown
### 이슈 제목

**증상**
(에러 메시지 또는 현상)

**원인**
(왜 발생했는지)

**해결**
(어떻게 해결했는지)
```

- 새로운 이슈 발생 시 해당 섹션에 추가한다
- 해결하지 못한 이슈도 기록해둔다

---

## 현재 개발 단계

TEAM + AUTH + MEMBER + ATTENDANCE + LEAVE + TASK + CALENDAR 완료 → **v1.0 달성**

---

## 포트폴리오 개선 TODO (나중에)

- `@Valid` — Request DTO 유효성 검증 추가
- 권한 검증 — 관리자 API에 role 체크 (Spring Security)
- 통합 테스트 — Testcontainers 실제 적용 or 기술 스택에서 제거
- `LEADER` role — MemberRole enum에 추가 (현재 ADMIN으로 대체 중)
