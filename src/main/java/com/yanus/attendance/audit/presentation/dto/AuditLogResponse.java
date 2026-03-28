package com.yanus.attendance.audit.presentation.dto;

import com.yanus.attendance.audit.domain.AuditAction;
import com.yanus.attendance.audit.domain.AuditLog;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.LocalDateTime;

public record AuditLogResponse (
        Long id,
        Long actorId,
        MemberRole actorRole,
        Long targetId,
        AuditAction action,
        String previousValue,
        String newValue,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorRole(),
                log.getTargetId(),
                log.getAction(),
                log.getPreviousValue(),
                log.getNewValue(),
                log.getCreatedAt()
        );
    }
}
