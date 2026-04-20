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

---

### 7. AttendanceSettingService — readOnly 트랜잭션 안에서 save() 호출

**증상**
자동 체크아웃 시간 조회 API(`GET /api/v1/settings/auto-checkout-time`) 호출 시 설정값이 DB에 없으면
`@Transactional(readOnly = true)` 안에서 save를 시도해 트랜잭션 쓰기 충돌 발생 가능.

**원인**
조회 로직에서 설정값이 없으면 기본값을 새로 생성 후 저장하는 `getOrCreateDefault()`를 호출하고 있었음.
```java
@Transactional(readOnly = true)
public AutoCheckoutTimeResponse getAutoCheckoutTime(Long actorId) {
    validateAdmin(actorId);
    AttendanceSetting setting = getOrCreateDefault();  // 내부에서 save() 호출
    return AutoCheckoutTimeResponse.from(setting);
}

private AttendanceSetting getOrCreateDefault() {
    return settingRepository.find()
            .orElseGet(() -> settingRepository.save(AttendanceSetting.createDefault()));
}
```

**해결**
조회 시에는 save 없이 메모리상 기본값만 반환하도록 변경. 저장은 변경 API(`PATCH`)에서만 수행.
```java
@Transactional(readOnly = true)
public AutoCheckoutTimeResponse getAutoCheckoutTime() {
    AttendanceSetting setting = settingRepository.find()
            .orElse(AttendanceSetting.createDefault());  // save 없음
    return AutoCheckoutTimeResponse.from(setting);
}
```
조회는 누구나 가능하도록 `validateAdmin`도 함께 제거. 변경(`PATCH`)만 ADMIN 전용으로 유지.

---

### 8. 자동 체크아웃이 설정 시간이 아닌 자정에 후처리됨

**증상**
관리자가 자동 체크아웃 시간을 `22:00`으로 설정해도 그 시각에 아무 일도 일어나지 않음.
다음날 자정에 스케줄러가 돌면서 **전날** 기록의 `check_out_time`만 `22:00:00`으로 저장됨.
실제 퇴근 처리가 22시에 이루어지지 않는 문제.

**원인**
`AttendanceScheduler`가 `@Scheduled(cron = "0 0 0 * * *")`로 **자정 1회만** 실행됨.
실행 시점에 `LocalDate.now().minusDays(1)`로 전날 기록을 조회해 일괄 처리하는 구조였음.
```java
@Scheduled(cron = "0 0 0 * * *")
public void autoCheckOut() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    attendanceService.autoCheckOut(yesterday);
}
```

**해결**
매분 실행으로 변경하고, 서비스 측에서 설정 시각 도달 여부를 검사 후 **오늘** 기록을 처리하도록 수정.
```java
// Scheduler
@Scheduled(cron = "0 * * * * *")  // 매분
public void autoCheckOut() {
    attendanceService.autoCheckOut(LocalDate.now(), LocalTime.now());
}

// Service
public void autoCheckOut(LocalDate workDate, LocalTime currentTime) {
    LocalTime configuredTime = attendanceSettingService.getAutoCheckoutTimeValue();
    if (currentTime.isBefore(configuredTime)) {
        return;
    }
    List<Attendance> workingAttendances =
            attendanceRepository.findAllByWorkDateAndStatus(workDate, AttendanceStatus.WORKING);
    LocalDateTime checkOutTime = workDate.atTime(configuredTime);
    workingAttendances.forEach(attendance -> attendance.checkOut(checkOutTime));
}
```
중복 처리 방지는 `WORKING` 상태 필터링으로 자연스럽게 해결됨 (첫 실행 후 LEFT 상태가 되어 다음 조회에서 제외).
테스트 시각 주입을 위해 `LocalTime currentTime` 파라미터 추가.
