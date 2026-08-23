# CALENDAR 도메인 트러블슈팅

---

### 1. 도메인 테스트 CRUD 범위 혼동

**증상**
도메인 테스트에 Create/Read/Update/Delete를 모두 넣으려 했으나 일부는 도메인 로직이 없었음.

**원인**
도메인 테스트는 엔티티 비즈니스 로직(유효성 검증, 상태 변경)만 테스트해야 하는데
Read/Delete는 Repository 레벨 동작이라 도메인 테스트 대상이 아님.

**해결**
- 도메인 테스트 = 엔티티 동작 (create 필드 검증, update 필드 변경, 유효성 검증 예외)
- CRUD 전체 = 서비스 테스트에서 커버
- 도메인 테스트에 `update_with_invalid_end_time` 추가로 마무리
