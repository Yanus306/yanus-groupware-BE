package com.yanus.attendance.audit.infrastructure;

import com.yanus.attendance.audit.domain.AuditLog;
import com.yanus.attendance.audit.domain.AuditLogRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuditLogRepository extends JpaRepository<AuditLog, Long>, AuditLogRepository {
}
