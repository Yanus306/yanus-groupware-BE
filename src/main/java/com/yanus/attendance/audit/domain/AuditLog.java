package com.yanus.attendance.audit.domain;

import com.yanus.attendance.member.domain.MemberRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private MemberRole actorRole;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AuditLog create(Long actorId, MemberRole actorRole, Long targetId,
                                  AuditAction action, String previousValue, String newValue) {
        AuditLog log = new AuditLog();
        log.actorId = actorId;
        log.actorRole = actorRole;
        log.targetId = targetId;
        log.action = action;
        log.previousValue = previousValue;
        log.newValue = newValue;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
