package com.yanus.attendance.audit.presentation.dto;

import java.time.LocalDateTime;

public record AuditLogResponse (
        Long id,
        Long actorId,
        String actorRole,
        Long targetId,
        String action,
        String previousValue,
        String newValue,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(com.yanus.attendance.audit.application.dto.AuditLogResponse response) {
        return new AuditLogResponse(
                response.id(),
                response.actorId(),
                response.actorRole().name(),
                response.targetId(),
                response.action().name(),
                response.previousValue(),
                response.newValue(),
                response.createdAt()
        );
    }
}
