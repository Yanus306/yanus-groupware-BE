package com.yanus.attendance.task.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.task.application.TaskService;
import com.yanus.attendance.task.presentation.dto.TaskCreateRequest;
import com.yanus.attendance.task.presentation.dto.TaskResponse;
import com.yanus.attendance.task.presentation.dto.TaskUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @AuthenticationPrincipal Long memberId,
            @RequestBody TaskCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.create(memberId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "MY") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if ("TEAM".equals(type)) {
            return ResponseEntity.ok(ApiResponse.success(taskService.getTeamTasks(memberId, startDate, endDate)));
        }
        return ResponseEntity.ok(ApiResponse.success(taskService.getMyTasks(memberId, startDate, endDate)));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable Long taskId,
            @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.update(taskId, request)));
    }

    @PatchMapping("/{taskId}/done")
    public ResponseEntity<ApiResponse<TaskResponse>> toggleDone(
            @PathVariable Long taskId ) {
        return ResponseEntity.ok(ApiResponse.success(taskService.toggleDone(taskId)));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long taskId) {
        taskService.delete(taskId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
