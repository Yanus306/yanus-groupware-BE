package com.yanus.attendance.audit;

import com.yanus.attendance.audit.domain.AuditLog;
import com.yanus.attendance.audit.domain.AuditLogRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeAuditLogRepository implements AuditLogRepository {

    private final Map<Long, AuditLog> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public AuditLog save(AuditLog auditLog) {
        ReflectionTestUtils.setField(auditLog, "id", sequence++);
        store.put(auditLog.getId(), auditLog);
        return auditLog;
    }

    @Override
    public List<AuditLog> findAll() {
        return new ArrayList<>(store.values());
    }
}
