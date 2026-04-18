# WORK SCHEDULE 도메인 트러블슈팅

---

### 1. 근무 일정 삭제 시 존재하지 않는 요일 예외 처리 누락

**증상**
존재하지 않는 요일로 `DELETE /api/v1/work-schedules/{dayOfWeek}` 요청 시 500 에러 반환.

**원인**
`deleteByMemberIdAndDayOfWeek()` 호출 전에 존재 여부 확인 로직이 없었음.

**해결**
```java
workScheduleRepository.findByMemberIdAndDayOfWeek(memberId, dayOfWeek)
        .orElseThrow(() -> new BusinessException(ErrorCode.WORK_SCHEDULE_NOT_FOUND));
workScheduleRepository.deleteByMemberIdAndDayOfWeek(memberId, dayOfWeek);
```

---

### 2. 팀/전체 근무 일정 조회 응답 구조 설계

**증상**
단순 리스트로 반환하면 같은 멤버의 요일별 일정이 각각 분리되어 응답됨.

**원인**
`WorkSchedule` 엔티티는 요일(day_of_week)별로 한 행씩 저장되는 구조.

**해결**
`MemberWorkScheduleResponse` DTO를 추가하여 멤버별로 그룹핑 후 반환.
```java
schedules.stream()
    .collect(Collectors.groupingBy(WorkSchedule::getMember))
    .entrySet().stream()
    .map(entry -> new MemberWorkScheduleResponse(...))
    .toList();
```

---

### 3. WorkScheduleEvent 등록 시 created_at NOT NULL 위반 (#149)

**증상**
날짜별 근무 일정 추가 API(`POST /api/v1/work-schedule-events`) 호출 시 DB 제약 위반.
```
ERROR: null value in column "created_at" of relation "work_schedule_event"
violates not-null constraint
```

**원인**
V17 마이그레이션에는 `created_at TIMESTAMP NOT NULL`이 있지만 `WorkScheduleEvent` 엔티티에는
대응되는 `createdAt` 필드 자체가 없어서 INSERT 시 null로 시도됨.

**해결**
엔티티에 `@CreationTimestamp` 달린 필드 추가.
```java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```
`@CreationTimestamp`는 Hibernate가 INSERT 시점에 자동으로 값을 채워주기 때문에 서비스 로직 변경 불필요.
