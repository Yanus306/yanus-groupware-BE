package com.yanus.attendance.audit.application;

import com.yanus.attendance.audit.domain.AuditAction;
import com.yanus.attendance.audit.domain.AuditLog;
import com.yanus.attendance.audit.domain.AuditLogRepository;
import com.yanus.attendance.audit.presentation.dto.AuditLogResponse;
import com.yanus.attendance.member.domain.MemberRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(Long actorId, MemberRole actorRole, Long targetId, AuditAction action, String previousValue, String newValue) {
        auditLogRepository.save(AuditLog.create(actorId, actorRole, targetId, action, previousValue, newValue));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> findAll() {
        return auditLogRepository.findAll().stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
