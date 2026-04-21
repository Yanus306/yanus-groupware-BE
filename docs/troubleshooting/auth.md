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
