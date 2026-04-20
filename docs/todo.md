1. 인덱스 (가장 급함)
   지금 마이그레이션에 인덱스가 거의 없어. 조회 쿼리에서 무조건 풀스캔 중.

자주 쓰이는 것들
```sql
CREATE INDEX idx_attendance_member_date ON attendance (member_id, work_date);
CREATE INDEX idx_attendance_work_date ON attendance (work_date);
CREATE INDEX idx_work_schedule_member ON work_schedule (member_id);
CREATE INDEX idx_leave_request_member ON leave_request (member_id);
CREATE INDEX idx_task_date ON task (date);
CREATE INDEX idx_refresh_token_member ON refresh_token (member_id);
```

2. N+1 문제
   findAll() 같은 목록 조회에서 member → team 이런 연관관계 로딩할 때 N+1 발생 가능성 높음. fetch join 또는 @EntityGraph 적용 필요.

3. 페이지네이션
   지금 GET /api/v1/attendances, GET /api/v1/members 같은 목록 API가 전체를 다 끌고 옴. 데이터 쌓이면 터짐. Pageable 적용 필요.

4. AttendanceSettlementService 계산 로직
   월별 지각 정산이 Java 레벨에서 루프 돌면서 계산하는 구조인데, 데이터 많아지면 느려짐. 핵심 집계는 DB 쿼리로 내리는 게 나음.

5. Redis 캐싱
   attendance_setting 테이블 — 자동 체크아웃 시간은 거의 안 바뀌는데 스케줄러 돌 때마다 조회함. Redis에 캐싱하면 DB 불필요한 hit 줄어듦
   refresh_token 조회 — 모든 인증 요청마다 DB hit, Redis로 대체 가능

6. 자동 체크아웃 스케줄러
   지금 매일 자정에 미퇴근자 전체 조회해서 처리하는 구조인데, 데이터 많아지면 한 번에 너무 많은 row를 업데이트함. Batch size 나눠서 처리하는 청크 방식이 안전함.

우선순위로 따지면:

인덱스 → 바로 효과 있고 리스크 없음
N+1 → 목록 API 많아서 체감 큼
페이지네이션 → API 스펙 변경 필요해서 프론트 협의 필요
나머지는 트래픽이 진짜 생겼을 때