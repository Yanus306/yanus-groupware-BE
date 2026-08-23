# TASK 도메인 트러블슈팅

---

### 1. TaskSpringDataRepository에 JpaRepository 메서드 중복 선언

**원인**
`JpaRepository<Task, Long>`을 상속하면 `save()`, `findById()`, `deleteById()`가 이미 제공되는데 인터페이스에 동일 메서드를 다시 선언함.

**해결**
`TaskSpringDataRepository`에서 중복 선언 제거.
```java
public interface TaskSpringDataRepository extends JpaRepository<Task, Long> {
}
```

---

### 2. Task 다중 멤버 추가 후 기존 테스트 컴파일 오류

**증상**
```
constructor TaskCreateRequest in record TaskCreateRequest cannot be applied to given types;
required: String,LocalDate,LocalTime,TaskPriority,Long,boolean,List<Long>
found:    String,LocalDate,LocalTime,TaskPriority,<null>,boolean
```

**원인**
TASK-008 다중 멤버 기능 추가로 `memberIds` 파라미터가 추가됐는데 기존 테스트는 이전 시그니처를 그대로 사용.

**해결**
```java
// Before
new TaskCreateRequest("제목", LocalDate.now(), null, TaskPriority.HIGH, null, false)

// After
new TaskCreateRequest("제목", LocalDate.now(), null, TaskPriority.HIGH, null, false, null)
```

---

### 3. TaskTest에서 QTask 정적 import 충돌

**증상**
`import static com.yanus.attendance.task.domain.QTask.task`로 인해 로컬 변수 `task`와 충돌.

**원인**
`QTask.task` 정적 필드와 `Task task = Task.createPersonal(...)` 로컬 변수 네이밍 충돌.

**해결**
`TaskTest.java`에서 정적 import 제거. 도메인 단위 테스트에서는 QueryDSL Q클래스 불필요.

---

### 4. Task 도메인 테스트에서 createPersonal/createTeam 파라미터 누락

**증상**
```
method createPersonal in class Task cannot be applied to given types;
required: Member,String,LocalDate,LocalTime,TaskPriority,List<Member>
found:    Member,String,LocalDate,LocalTime,TaskPriority
```

**원인**
TASK-008로 `List<Member> members` 파라미터가 추가됐는데 기존 도메인 테스트는 파라미터 없이 호출.

**해결**
도메인 테스트의 모든 `createPersonal`, `createTeam` 호출에 마지막 인자로 `null` 추가.

---

### 5. FakeMemberRepository에 findAllByIds 미구현

**증상**
```
FakeMemberRepository is not abstract and does not override abstract method findAllByIds(List<Long>) in MemberRepository
```

**해결**
```java
@Override
public List<Member> findAllByIds(List<Long> ids) {
    return store.values().stream()
            .filter(m -> ids.contains(m.getId()))
            .toList();
}
```

---

### 6. TaskServiceTest에 java.util.List import 누락

**증상**
```
cannot find symbol
symbol: variable List
```

**해결**
```java
import java.util.List;
```
