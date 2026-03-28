# SECURITY 트러블슈팅

---

### 1. Member.create() status 파라미터 무시

**증상**
`MemberStatus.INACTIVE`를 넘겨 생성해도 항상 ACTIVE 상태로 저장됨.
`team_lead_can_not_change_inactive_member_error` 테스트가 예외를 던지지 않고 통과.

**원인**
`Member.create()` 내부에서 파라미터를 무시하고 항상 `ACTIVE`로 하드코딩.
```java
member.status = MemberStatus.ACTIVE; // status 파라미터 무시
```

**해결**
파라미터를 그대로 사용하도록 수정.
```java
member.status = status;
```

---

### 2. 리프레시 토큰 회전 시 동일 토큰 발급

**증상**
`assertThat(response.refreshToken()).isNotEqualTo(oldRefreshToken)` 테스트 실패.
새 토큰과 기존 토큰이 동일한 값.

**원인**
`JwtTokenProvider.createRefreshToken()`에 고유 식별자가 없어 같은 밀리초에
호출하면 payload가 동일해 서명값도 동일한 JWT가 생성됨.

**해결**
`jti` 클레임에 UUID 추가.
```java
public String createRefreshToken(Long memberId) {
    return Jwts.builder()
            .subject(String.valueOf(memberId))
            .id(UUID.randomUUID().toString())  // 추가
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(secretKey)
            .compact();
}
```

---

### 3. 로그인 5번째 실패 시 UNAUTHORIZED 반환

**증상**
5회 실패 시 `ACCOUNT_LOCKED`가 아닌 `UNAUTHORIZED` 반환.

**원인**
`recordLoginFailure()` 호출로 잠금 처리 후에도 이어서 `UNAUTHORIZED`를 throw.
다음 요청(6번째)부터 `isLocked()` 체크에서 잡히는 구조였음.

**해결**
실패 기록 후 잠금 여부를 즉시 재확인.
```java
if (!passwordEncoder.matches(request.password(), member.getPassword())) {
    member.recordLoginFailure();
    if (member.isLocked()) {
        throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
    }
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
}
```

---

### 4. TeamServiceTest 멤버 있는 팀 삭제 시 TEAM_NOT_FOUND 먼저 발생

**증상**
`멤버_있는_팀_삭제_예외` 테스트에서 `TEAM_HAS_MEMBERS` 대신 `TEAM_NOT_FOUND` 발생.

**원인**
`FakeTeamRepository`는 별도 인스턴스(`fakeRepo`)라 sequence가 1부터 시작.
`fakeRepo.save(Team.create("1팀"))` → id=1 인데 `deleteTeam(member.getId(), 2L)` 로 호출.

**해결**
```java
assertThatThrownBy(() -> service.deleteTeam(member.getId(), 1L)) // 2L → 1L
```
