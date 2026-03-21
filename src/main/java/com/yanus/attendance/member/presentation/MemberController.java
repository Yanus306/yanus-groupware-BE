package com.yanus.attendance.member.presentation;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.member.application.MemberService;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.presentation.dto.MemberResponse;
import com.yanus.attendance.member.presentation.dto.ProfileUpdateRequest;
import com.yanus.attendance.member.presentation.dto.RoleChangeRequest;
import com.yanus.attendance.team.domain.TeamName;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemberResponse>>> findAll(
            @RequestParam(required = false) TeamName teamName,
            @RequestParam(required = false) MemberRole role) {
        return ResponseEntity.ok(ApiResponse.success(memberService.findAll(teamName, role)));
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<MemberResponse>> findById(@PathVariable Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.findById(memberId)));
    }

    @GetMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> changeRole(
            @PathVariable Long memberId,
            @RequestParam RoleChangeRequest request) {
        memberService.changeRole(memberId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long memberId) {
        memberService.deactivate(memberId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long memberId) {
        memberService.activate(memberId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal Long memberId,
            @RequestBody ProfileUpdateRequest request) {
        memberService.updateProfile(memberId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
