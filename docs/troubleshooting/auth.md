# AUTH 도메인 트러블슈팅

---

### 1. 비활성화(INACTIVE) 멤버 로그인 차단 누락

**증상**
관리자가 멤버를 `INACTIVE` 처리해도 해당 멤버가 정상적으로 로그인 가능.

**원인**
`AuthService.login()`에서 비밀번호 검증만 하고 `status` 체크 로직이 없었음.

**해결**
`Member.isInactive()` 메서드 추가 후 로그인 시 상태 체크.
```java
if (member.isInactive()) {
    throw new BusinessException(ErrorCode.MEMBER_INACTIVE);
}
```
`ErrorCode.MEMBER_INACTIVE(HttpStatus.FORBIDDEN)` 추가.

---

### 2. AuthServiceTest 컴파일 에러 — constructor cannot be applied

**증상**
```
error: constructor AuthService in class AuthService cannot be applied to given types;
required: MemberRepository,RefreshTokenRepository,PasswordEncoder,JwtProvider,EmailVerificationService
found:    MemberRepository,RefreshTokenRepository,PasswordEncoder,JwtProvider
```

**원인**
이메일 인증 기능 추가로 `AuthService` 생성자에 `EmailVerificationService` 파라미터가 추가되었는데
기존 `AuthServiceTest`의 `setUp()`에서는 예전 시그니처로 인스턴스 생성 중이었음.

**해결**
setUp에서 `EmailVerificationService`를 먼저 만들어 AuthService 생성자에 전달.
```java
@BeforeEach
void setUp() {
    memberRepository = new FakeMemberRepository();
    tokenRepository = new FakeRefreshTokenRepository();
    EmailService emailService = mock(EmailService.class);
    EmailVerificationService verificationService =
            new EmailVerificationService(new FakeEmailVerificationTokenRepository(), memberRepository, emailService);
    authService = new AuthService(memberRepository, tokenRepository, passwordEncoder, jwtProvider, verificationService);
}
```

---

### 3. AuthController — EmailVerificationService가 아닌 EmailService 주입

**증상**
이메일 인증 API가 호출은 되는데 실제 검증/인증 메일 발송 로직이 동작하지 않음.

**원인**
`AuthController`에서 `EmailVerificationService` 대신 `EmailService`를 주입했고, 메서드도 `sendVerificationEmail`로 잘못 호출.
재발송 API에서는 Controller가 직접 `MemberRepository`로 이메일 조회를 시도하는 레이어 침범까지 있었음.
```java
// Bad
@PostMapping("/verify-email")
public void verifyEmail(@RequestBody VerifyEmailRequest request) {
    emailService.sendVerificationEmail(request.token());  // 검증 아닌 발송 호출
}

@PostMapping("/verify-email/resend")
public void resendVerification(@RequestBody ResendVerificationRequest request) {
    Member member = memberRepository.findByEmail(request.email())...;  // Controller에서 Repository 직접 호출
}
```

**해결**
`EmailVerificationService` 주입으로 교체하고, resend 로직은 서비스 안으로 이동.
```java
private final EmailVerificationService emailVerificationService;

@PostMapping("/verify-email")
public void verifyEmail(@RequestBody VerifyEmailRequest request) {
    emailVerificationService.verify(request.token());
}

@PostMapping("/verify-email/resend")
public void resendVerification(@RequestBody ResendVerificationRequest request) {
    emailVerificationService.resend(request.email());
}
```

---

### 4. 이메일 인증 토큰 V18 마이그레이션 FK 참조 오류

**증상**
```
ERROR: column "id" referenced in foreign key constraint does not exist
Migration V18__create_email_verification_token.sql failed
```
서버 기동 실패.

**원인**
`member` 테이블의 PK는 `member_id`인데 V18에서 `REFERENCES member(id)`로 잘못 참조.
(V5에서 같은 실수를 반복한 사례)

**해결**
```sql
-- Before
member_id BIGINT NOT NULL REFERENCES member(id)

-- After
member_id BIGINT NOT NULL REFERENCES member(member_id)
```
