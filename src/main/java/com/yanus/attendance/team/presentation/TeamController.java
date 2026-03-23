package com.yanus.attendance.team.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.team.application.TeamService;
import com.yanus.attendance.team.presentation.dto.TeamResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "팀", description = "팀 전체 조회, 단일 조회")
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(teamService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(teamService.findById(id)));
    }
}
