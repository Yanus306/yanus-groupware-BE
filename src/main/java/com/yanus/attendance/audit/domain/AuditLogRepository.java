package com.yanus.attendance.audit.domain;

import java.util.List;

public interface AuditLogRepository {
    AuditLog save(AuditLog auditLog);

    List<AuditLog> findAll();
}
