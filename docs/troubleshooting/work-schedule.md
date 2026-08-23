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

### 3. 날짜별 근무 일정이 예외 판정과 팀 조회에 반영되지 않음 (#191)

**증상**
날짜별 근무 일정만 추가한 날짜가 출퇴근 예외 조회에서 근무일로 반영되지 않고 `NO_SCHEDULE`로 처리될 수 있음.
또한 본인 날짜별 일정 조회 API만 있어 팀원/관리자 화면에서 다른 사람의 날짜별 일정이 보이지 않음.

**원인**
`AttendanceExceptionService`가 반복 일정(`WorkSchedule`)만 조회하고 날짜별 일정(`WorkScheduleEvent`)을 보지 않았음.
`WorkScheduleEventService`에도 팀/전체 조회 유스케이스가 없었음.

**해결**
예외 판정 시 날짜별 일정을 반복 일정보다 우선 적용하도록 `WorkScheduleEventRepository`를 주입하고,
팀/전체 날짜별 일정 조회 API를 추가함.

---

### 4. 반복 근무 일정 중 특정 날짜만 휴무 처리 필요 (#194, #195, #196)

**증상**
매주 반복 근무 일정은 유지하면서 특정 날짜 하루만 휴무로 빼는 방법이 없었음.
해당 날짜만 쉬어도 반복 일정 때문에 미출근 예외나 지각 정산 대상에 포함될 수 있음.

**원인**
`WorkScheduleEvent`가 날짜별 근무 시간 override만 표현하고, 명시적 휴무 상태를 표현하지 못했음.
예외 판정과 정산도 날짜별 이벤트를 모두 근무 일정으로 해석했음.

**해결**
`WorkScheduleEventType`을 추가해 `WORKING`과 `DAY_OFF`를 구분함.
`DAY_OFF`는 시작/종료 시간을 null로 저장하고, 예외 판정과 정산에서 반복 일정보다 우선하는 명시적 휴무로 처리함.
