package com.yanus.attendance.team.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.team.application.TeamService;
import com.yanus.attendance.team.application.dto.TeamResponse;
import com.yanus.attendance.team.presentation.dto.TeamCreateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀", description = "팀 전체 조회, 단일 조회")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamCreateRequest>>> findAll() {
        List<TeamCreateRequest> response = teamService.findAll().stream()
                .map(TeamCreateRequest::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamCreateRequest>> findById(
            @PathVariable Long id) {
        TeamResponse response = teamService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(TeamCreateRequest.from(response)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamCreateRequest>> createTeam(
            @AuthenticationPrincipal Long memberId,
            @RequestBody TeamCreateRequest request) {
        TeamResponse response = teamService.createTeam(memberId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(TeamCreateRequest.from(response)));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long teamId) {
        teamService.deleteTeam(memberId, teamId);
        return ResponseEntity.noContent().build();
    }
}
