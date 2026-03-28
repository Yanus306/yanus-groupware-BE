# ATTENDANCE 도메인 트러블슈팅

---

### 1. BIGINT만 선언하면 PostgreSQL에서 자동 증가가 되지 않음

**증상**
`attendance_id`가 null로 저장되거나 PK 자동 생성이 안 됨.

**원인**
`BIGINT PRIMARY KEY`만 선언하면 PostgreSQL에서 자동 증가가 되지 않음.
MySQL의 `AUTO_INCREMENT`와 달리 PostgreSQL은 `BIGSERIAL` 사용.

**해결**
```sql
-- Before
attendance_id BIGINT PRIMARY KEY

-- After
attendance_id BIGSERIAL PRIMARY KEY
```

---

### 2. V5 SQL - 외래키 참조 컬럼 오류

**증상**
```
ERROR: column "id" referenced in foreign key constraint does not exist
```

**원인**
`member` 테이블의 PK 컬럼명은 `member_id`인데 `REFERENCES member(id)`로 잘못 참조.

**해결**
```sql
-- Before
member_id BIGINT NOT NULL REFERENCES member(id)

-- After
member_id BIGINT NOT NULL REFERENCES member(member_id)
```

---

### 3. 동일 경로에 두 메서드가 매핑되어 contextLoads() 실패

**증상**
```
AttendanceApplicationTests > contextLoads() FAILED
    Caused by: java.lang.IllegalStateException at AbstractHandlerMethodMapping.java:677
```

**원인**
`getAttendancesByFilter()` 메서드 추가 시 기존 `getAttendancesByDate()` 메서드를 삭제하지 않아
`GET /api/v1/attendances`에 두 메서드가 중복 매핑됨.

**해결**
`AttendanceController`에서 기존 `getAttendancesByDate()` 메서드 삭제.
