package com.yanus.attendance.task.application;

import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.task.domain.Task;
import com.yanus.attendance.task.domain.TaskPriority;
import com.yanus.attendance.task.domain.TaskQueryRepository;
import com.yanus.attendance.task.domain.TaskRepository;
import com.yanus.attendance.task.presentation.dto.TaskCreateRequest;
import com.yanus.attendance.task.presentation.dto.TaskResponse;
import com.yanus.attendance.task.presentation.dto.TaskUpdateRequest;
import com.yanus.attendance.team.domain.TeamRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final MemberRepository memberRepository;
    private final TaskQueryRepository taskQueryRepository;

    public TaskResponse create(Long memberId, TaskCreateRequest request) {
        Member creator = findMember(memberId);

        if (request.isTeamTask()) {
            Member assignee = request.assigneeId() != null ? findMember(request.assigneeId()) : creator;
            Task task = Task.createTeam(creator, assignee, creator.getTeam(),
                    request.title(), request.date(), request.time(), request.priority());
            taskRepository.save(task);
            return TaskResponse.from(task);
        }

        Task task = Task.createPersonal(creator, request.title(), request.date(), request.time(), request.priority());
        taskRepository.save(task);
        return TaskResponse.from(task);
    }

    public TaskResponse toggleDone(Long taskId) {
        Task task = findTask(taskId);
        task.toggleDone();
        return TaskResponse.from(task);
    }

    public TaskResponse update(Long taskId, TaskUpdateRequest request) {
        Task task = findTask(taskId);
        task.update(request.title(), request.date(), request.time(), request.priority());
        return TaskResponse.from(task);
    }

    public void delete(Long taskId) {
        taskRepository.deleteById(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasks(Long memberId, LocalDate startDate, LocalDate endDate) {
        return taskQueryRepository.findMyTasks(memberId, startDate, endDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTeamTasks(Long teamId, LocalDate startDate, LocalDate endDate) {
        return taskQueryRepository.findTeamTasks(teamId, startDate, endDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
