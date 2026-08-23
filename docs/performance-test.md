# 성능 테스트

## 환경

| 항목 | 내용 |
|------|------|
| 도구 | k6 |
| 대상 서버 | api.yanus.bond (단일 서버) |
| OS | Ubuntu 24.04 |
| DB | PostgreSQL (별도 서버) |

---

## 설치

```bash
brew install k6        # macOS
sudo snap install k6   # Ubuntu
```

---

## 시나리오

### 1. 출근 체크인 부하 테스트

```bash
k6 run tests/k6/attendance.js
```

**목표**
- p95 응답시간 500ms 이하
- 에러율 1% 미만

**단계**
| 단계 | 시간 | VU |
|------|------|----|
| 워밍업 | 10s | 20 |
| 부하 | 30s | 500 |
| 쿨다운 | 10s | 0 |

---

### 2. 읽기 부하 테스트 (멤버/팀 조회)

```bash
k6 run tests/k6/member.js
```

**목표**
- p95 응답시간 300ms 이하
- VU 1000명 기준

---

### 3. 복합 시나리오 (실사용 패턴)

```bash
MEMBER_TOKEN=xxx ADMIN_TOKEN=yyy k6 run tests/k6/scenario.js
```

**시나리오 구성**
| 역할 | VU | 행동 |
|------|----|------|
| 일반 멤버 | 30 | 출근 → 할일 조회 → 캘린더 조회 |
| 관리자 | 5 | 출근 현황 조회 → 전체 멤버 조회 |

---

## 결과 기록

> 테스트 후 아래 표에 결과를 업데이트한다.

### 출근 체크인 API

| 날짜 | VU | 평균 응답시간 | p95 | TPS | 에러율 | 비고 |
|------|----|-------------|-----|-----|--------|------|
| - | - | - | - | - | - | 최초 측정 전 |

### 멤버 목록 조회 API

| 날짜 | VU | 평균 응답시간 | p95 | TPS | 에러율 | 비고 |
|------|----|-------------|-----|-----|--------|------|
| - | - | - | - | - | - | 최초 측정 전 |

---

## 병목 분석 방법

### N+1 쿼리 확인
`application.properties`에서 SQL 로그 활성화 후 쿼리 수 확인.
```properties
spring.jpa.show-sql=true
logging.level.org.hibernate.SQL=DEBUG
```

### 실행 계획 확인
```sql
EXPLAIN ANALYZE
SELECT * FROM attendance WHERE work_date = '2026-03-26';
```

### HikariCP 커넥션 풀 로그
```properties
logging.level.com.zaxxer.hikari=DEBUG
```

---

## 개선 이력

> 병목 발견 → 개선 → 재측정 결과를 아래에 기록한다.

| 날짜 | 문제 | 원인 | 개선 방법 | 개선 전 p95 | 개선 후 p95 |
|------|------|------|----------|------------|------------|
| - | - | - | - | - | - |

---

## HTML 리포트 추출

```bash
k6 run --out json=result.json tests/k6/attendance.js
```
