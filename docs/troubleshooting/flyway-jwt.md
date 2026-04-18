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

### 5. 운영 서버 V17 Checksum 불일치 — 파일 재포맷만 해도 발생

**증상**
서버 재기동 시 Flyway 마이그레이션 실패로 서비스 전체가 죽음.
```
FlywayValidateException: Validate failed: Migration checksum mismatch for migration version 17
-> Applied to database : 1234567890
-> Resolved locally    : 561208955
```

**원인**
V17 파일 본문 SQL은 바꾸지 않았지만, 나중 커밋(V18 추가 시)에서 V17 파일이 IDE 저장 시 들여쓰기/개행이
재포맷되면서 체크섬이 바뀜. Flyway는 파일 바이트 단위로 체크섬을 계산하기 때문에 공백 변경도 실패로 판정.

**해결**
운영 DB는 `docker compose down -v`로 초기화 불가 → `flyway_schema_history` 테이블의 체크섬 값을 직접 수정.
```bash
psql -U yanus -d yanus -c \
  "UPDATE flyway_schema_history SET checksum = 561208955 WHERE version = '17';"
```
이후 서버 재기동하면 정상 부팅.

**예방**
적용된 마이그레이션 파일은 **포맷팅 포함 일체 수정 금지**. 새 변경은 무조건 새 버전 파일로 추가.

---

### 6. JWT_SECRET systemd 환경변수 로딩 실패

**증상**
서버 재기동 시 JWT 관련 빈 초기화 단계에서 실패.
```
jwt.secret must not be null
```

**원인**
`application-prod.properties`가 `jwt.secret=${JWT_SECRET}`로 환경변수를 참조하지만
systemd 서비스 파일에 `EnvironmentFile` 경로가 누락되거나 파일이 비어 있어 주입 실패.

**해결**
systemd 서비스 파일(`/etc/systemd/system/yanus.service`)에 `EnvironmentFile` 명시.
```
[Service]
EnvironmentFile=/home/yanus/app/.env
ExecStart=/usr/bin/java -jar /home/yanus/app/app.jar
```
`.env` 파일 예시:
```
JWT_SECRET=실제-최소-32자-시크릿-키
```
변경 후 `sudo systemctl daemon-reload && sudo systemctl restart yanus`.

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
