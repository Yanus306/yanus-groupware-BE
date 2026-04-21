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

### 3. V15 audit_log 마이그레이션 AUTO_INCREMENT 문법 오류 → 502

**증상**
```
FlywayMigrateException: Script V15__create_audit_log.sql failed
ERROR: syntax error at or near "AUTO_INCREMENT"
```
서버가 기동되지 않아 모든 API 502 응답.

**원인**
`AUTO_INCREMENT`는 MySQL 문법. PostgreSQL에서는 사용 불가.

**해결**
```sql
-- Before
audit_log_id BIGINT AUTO_INCREMENT PRIMARY KEY

-- After
audit_log_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY
```

---

### 4. 로컬 신규 DB 세팅 시 회원가입 불가 — 닭이냐 달걀이냐

**증상**
회원가입 요청 시 `NOT_FOUND` 응답. API가 아무것도 안 되는 것처럼 보임.

**원인**
V12 마이그레이션에서 기본 팀 데이터를 전부 삭제함.
팀이 없으면 회원가입 불가 → 유저가 없으면 팀 생성 API(ADMIN 전용) 호출 불가.

**해결**
Docker로 직접 초기 데이터 INSERT.
```bash
docker exec -i yanus-postgres psql -U yanus -d yanus -c "
INSERT INTO team (name, created_at) VALUES ('개발팀', NOW()), ('신입', NOW()) ON CONFLICT (name) DO NOTHING;
INSERT INTO member (name, email, password, role, team_id, status, created_at)
SELECT '관리자', 'admin@test.com',
'\$2a\$10\$Wb1BjoFhUBjxDHyGv94MTOtVmKJ2tjQPUy5i19qe/Tbi3QJ8s4eBS',
'ADMIN', team_id, 'ACTIVE', NOW()
FROM team WHERE name = '개발팀' ON CONFLICT (email) DO NOTHING;
"
```
또는 `data.sql` + `application-local.properties`에 `spring.sql.init.mode=always` / `spring.jpa.defer-datasource-initialization=true` 설정.

---

### 5. 로컬에서 체크인 시 INVALID_ATTENDANCE_IP (403)

**증상**
로컬에서 체크인 API 호출 시 `INVALID_ATTENDANCE_IP` 403 응답.

**원인**
체크인은 `220.69` 대역 IP만 허용. 로컬 요청은 `127.0.0.1`로 들어와 차단됨.

**해결**
`X-Forwarded-For` 헤더로 허용 IP를 흉내내어 테스트.
```bash
curl -X POST http://localhost:8080/api/v1/attendances/check-in \
  -H "Authorization: Bearer {토큰}" \
  -H "X-Forwarded-For: 220.69.1.1"
```
Swagger UI는 임의 헤더 추가 불가 → curl 사용 필요.

---

### 6. 동일 경로에 두 메서드가 매핑되어 contextLoads() 실패

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
