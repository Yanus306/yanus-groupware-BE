# Flyway / JWT 트러블슈팅

---

## Flyway

### 1. V4 마이그레이션 SQL 구문 오류 (백틱)

**증상**
```
ERROR: syntax error at or near "``"
Position: 1
Location: db/migration/V4__add_member_status.sql, Line: 3
```

**원인**
`V4__add_member_status.sql` 파일 끝에 백틱(`` ` ``)이 2개 포함되어 있었음.
PostgreSQL은 MySQL 방식의 백틱을 지원하지 않음.

**해결**
3번째 줄 백틱 제거.
```sql
ALTER TABLE member
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
```

---

### 2. V4 Checksum 불일치

**증상**
```
Migration checksum mismatch for migration version 4
-> Applied to database : -1119497084
-> Resolved locally    : -1211633977
```

**원인**
V4 SQL 파일을 DB에 적용한 뒤 파일 내용을 수정함.
Flyway는 이미 적용된 마이그레이션 파일이 변경되면 체크섬 불일치로 실행 거부.

**해결**
개발 환경이므로 DB 초기화로 해결.
```bash
docker compose down -v && docker compose up -d
```
운영 환경이라면 `flywayRepair` 사용.

---

### 3. V5 PK 컬럼명 불일치

**증상**
```
Schema-validation: missing column [attendance_id] in table [attendance]
```

**원인**
V5 SQL에서 PK를 `id`로 작성했으나 엔티티는 `@Column(name = "attendance_id")`로 매핑.

**해결**
```sql
attendance_id BIGSERIAL PRIMARY KEY
```

---

### 4. V5 외래키 참조 컬럼 오류

**증상**
```
ERROR: column "id" referenced in foreign key constraint does not exist
```

**원인**
`member` 테이블 PK가 `member_id`인데 `REFERENCES member(id)`로 잘못 참조.

**해결**
```sql
-- Before
member_id BIGINT NOT NULL REFERENCES member(id)

-- After
member_id BIGINT NOT NULL REFERENCES member(member_id)
```

---

## JWT

### 1. @Value 잘못된 import

**증상**
`jwt.secret` 값이 주입되지 않아 NullPointerException 발생.

**원인**
`@Value`를 `lombok.Value`로 import함.

**해결**
```java
// Before
import lombok.Value;

// After
import org.springframework.beans.factory.annotation.Value;
```

---

### 2. 환경변수 로딩 실패

**증상**
서버 실행 시 `jwt.secret` 값이 로딩되지 않음.

**원인**
`application-local.properties`에서 키를 `JWT_SECRET`(대문자)으로 작성했으나
`application.properties`는 `jwt.secret`(소문자)으로 바인딩.

**해결**
```properties
jwt.secret=${JWT_SECRET}
```
