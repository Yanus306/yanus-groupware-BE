# TEAM 도메인 트러블슈팅

---

### 1. TeamName enum → String 변환 시 @Enumerated 어노테이션 잔존

**증상**
```
Caused by: org.hibernate.AnnotationException
  at BasicValueBinder.java:814
```
`contextLoads()` 실패, 서버 502 에러 발생.

**원인**
`Team.name` 타입을 `TeamName` enum → `String`으로 변경했으나 `@Enumerated(EnumType.STRING)` 어노테이션을 제거하지 않음.
Hibernate는 `String` 타입 필드에 `@Enumerated`가 붙으면 AnnotationException 발생.

**해결**
```java
// Before
@Enumerated(EnumType.STRING)
@Column(name = "name", nullable = false, unique = true, length = 50)
private String name;

// After
@Column(name = "name", nullable = false, unique = true, length = 50)
private String name;
```

---

### 2. V12 마이그레이션 FK 제약 조건 위반으로 서버 502 에러

**증상**
```
ERROR: update or delete on table "team" violates foreign key constraint
Detail: Key (team_id)=(1) is still referenced from table "member"
Location: db/migration/V12__remove_default_teams.sql
```
배포 후 서버 502 Bad Gateway 발생.

**원인**
`DELETE FROM team` 실행 시 `member` 테이블이 `team_id`를 FK로 참조 중인 팀이 존재하여 삭제 불가.
CI 환경에서는 데이터가 없어 통과했으나 운영 환경에서는 실제 멤버 데이터가 있었음.

**해결**
멤버가 소속된 팀은 제외하고 참조 없는 팀만 삭제.
```sql
DELETE FROM team
WHERE team_id NOT IN (
    SELECT DISTINCT team_id FROM member WHERE team_id IS NOT NULL
);
```
운영 DB에서 Flyway 실패 기록 제거 후 재배포.
```bash
sudo -u postgres psql -d yanus -c "DELETE FROM flyway_schema_history WHERE version = '12';"
```
