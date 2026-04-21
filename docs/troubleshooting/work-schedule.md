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
