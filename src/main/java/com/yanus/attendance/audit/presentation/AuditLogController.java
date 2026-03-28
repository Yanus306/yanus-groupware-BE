package com.yanus.attendance.audit.presentation;

import com.yanus.attendance.audit.application.AuditLogService;
import com.yanus.attendance.audit.presentation.dto.AuditLogResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "감시 로그", description = "관리자 감사 로그 조회")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findAll()));
    }
}
