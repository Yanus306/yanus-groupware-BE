package com.yanus.attendance.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.task.FakeTaskQueryRepository;
import com.yanus.attendance.task.FakeTaskRepository;
import com.yanus.attendance.task.domain.TaskPriority;
import com.yanus.attendance.task.domain.TaskRepository;
import com.yanus.attendance.task.presentation.dto.TaskCreateRequest;
import com.yanus.attendance.task.presentation.dto.TaskResponse;
import com.yanus.attendance.task.presentation.dto.TaskUpdateRequest;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class TaskServiceTest {

    private TaskService taskService;
    private MemberRepository memberRepository;
    private FakeTaskQueryRepository taskQueryRepository;

    @BeforeEach
    void setUp() {
        TaskRepository taskRepository = new FakeTaskRepository();
        memberRepository = new FakeMemberRepository();
        taskQueryRepository = new FakeTaskQueryRepository();
        taskService = new TaskService(taskRepository, memberRepository, taskQueryRepository);
    }

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("개인 Task 생성")
    void create_personal_task() {
        // given
        Member member = createMember();
        TaskCreateRequest request = new TaskCreateRequest("테스트 작성", LocalDate.now(), LocalTime.of(9, 0), TaskPriority.HIGH, null, false);

        // when
        TaskResponse response = taskService.create(member.getId(), request);

        // then
        assertThat(response.title()).isEqualTo("테스트 작성");
        assertThat(response.done()).isFalse();
        assertThat(response.isTeamTask()).isFalse();
    }

    @Test
    @DisplayName("Task 완료 토글")
    void toggle_done() {
        // given
        Member member = createMember();
        TaskCreateRequest request = new TaskCreateRequest("테스트 작성", LocalDate.now(), null, TaskPriority.MEDIUM, null, false);
        TaskResponse created = taskService.create(member.getId(), request);

        // when
        TaskResponse response = taskService.toggleDone(created.id());

        // then
        assertThat(response.done()).isTrue();
    }

    @Test
    @DisplayName("Task 수정")
    void update_task() {
        // given
        Member member = createMember();
        TaskCreateRequest createRequest = new TaskCreateRequest("원래 제목", LocalDate.now(), null, TaskPriority.LOW, null, false);
        TaskResponse created = taskService.create(member.getId(), createRequest);

        // when
        TaskUpdateRequest updateRequest = new TaskUpdateRequest("수정된 제목", LocalDate.now().plusDays(1), null, TaskPriority.HIGH);
        TaskResponse response = taskService.update(created.id(), updateRequest);

        // then
        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    @DisplayName("Task 삭제 후 조회 시 예외 발생")
    void delete_task() {
        // given
        Member member = createMember();
        TaskCreateRequest request = new TaskCreateRequest("삭제할 Task", LocalDate.now(), null, TaskPriority.LOW, null, false);
        TaskResponse created = taskService.create(member.getId(), request);

        // when
        taskService.delete(created.id());

        // then
        assertThatThrownBy(() -> taskService.toggleDone(created.id()))
                .isInstanceOf(BusinessException.class);
    }
}
