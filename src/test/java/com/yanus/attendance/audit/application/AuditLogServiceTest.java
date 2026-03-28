package com.yanus.attendance.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.audit.FakeAuditLogRepository;
import com.yanus.attendance.audit.domain.AuditAction;
import com.yanus.attendance.audit.presentation.dto.AuditLogResponse;
import com.yanus.attendance.member.domain.MemberRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AuditLogServiceTest {

    private AuditLogService auditLogService;
    private FakeAuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository = new FakeAuditLogRepository();
        auditLogService = new AuditLogService(auditLogRepository);
    }

    @Test
    @DisplayName("감사 로그 저장")
    void 감사_로그_저장() {
        // when
        auditLogService.log(1L, MemberRole.ADMIN, 2L, AuditAction.ROLE_CHANGE, "MEMBER", "TEAM_LEAD");

        // then
        List<AuditLogResponse> logs = auditLogService.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).action()).isEqualTo(AuditAction.ROLE_CHANGE);
        assertThat(logs.get(0).previousValue()).isEqualTo("MEMBER");
        assertThat(logs.get(0).newValue()).isEqualTo("TEAM_LEAD");
    }

    @Test
    @DisplayName("전체 감사 로그 조회")
    void 전체_감사_로그_조회() {
        // given
        auditLogService.log(1L, MemberRole.ADMIN, 2L, AuditAction.ROLE_CHANGE, "MEMBER", "TEAM_LEAD");
        auditLogService.log(1L, MemberRole.ADMIN, 3L, AuditAction.DEACTIVATE, "ACTIVE", "INACTIVE");

        // when
        List<AuditLogResponse> logs = auditLogService.findAll();

        // then
        assertThat(logs).hasSize(2);
    }
}
