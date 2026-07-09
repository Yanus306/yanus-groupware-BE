package com.yanus.attendance.member.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.member.application.MemberService;
import com.yanus.attendance.member.application.dto.ProfileUpdateCommand;
import com.yanus.attendance.member.application.dto.RoleChangeCommand;
import com.yanus.attendance.member.application.dto.TeamChangeCommand;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.member.presentation.dto.ProfileUpdateRequest;
import com.yanus.attendance.member.presentation.dto.RoleChangeRequest;
import com.yanus.attendance.member.presentation.dto.TeamChangeRequest;
import com.yanus.attendance.member.presentation.dto.TemporaryPasswordResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@Tag(name = "멤버", description = "멤버 조회, 멤버 수정, 역할 수정, 상태 비활성화, 상태 활성화")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberResponse>>> findAll(
            @RequestParam(required = false) String teamName,
            @RequestParam(required = false) MemberRole role) {
        List<MemberResponse> response = memberService.findAll(teamName, role).stream()
                .map(MemberResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<MemberResponse>> findById(
            @PathVariable Long memberId) {
        com.yanus.attendance.member.application.dto.MemberResponse response = memberService.findById(memberId);
        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(response)));
    }

    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> changeRole(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long memberId,
            @RequestBody RoleChangeRequest request) {
        memberService.changeRole(actorId, memberId, new RoleChangeCommand(request.role()));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long memberId) {
        memberService.deactivate(actorId, memberId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{memberId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long memberId) {
        memberService.activate(actorId, memberId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PatchMapping("/{memberId}/team")
    public ResponseEntity<ApiResponse<Void>> changeTeam(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long memberId,
            @RequestBody TeamChangeRequest request) {
        memberService.changeTeam(actorId, memberId, new TeamChangeCommand(request.teamId()));
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{memberId}/reset-password")
    public ResponseEntity<ApiResponse<TemporaryPasswordResponse>> resetPassword(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long memberId) {
        com.yanus.attendance.member.application.dto.TemporaryPasswordResponse response
                = memberService.resetPassword(actorId, memberId);
        return ResponseEntity.ok(ApiResponse.success(TemporaryPasswordResponse.from(response)));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ProfileUpdateRequest request) {
        memberService.updateProfile(memberId, new ProfileUpdateCommand(request.name(), request.password()));
        return ResponseEntity.ok(ApiResponse.success());
    }
}
