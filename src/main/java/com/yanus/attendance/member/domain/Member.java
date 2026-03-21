package com.yanus.attendance.member.domain;

import com.yanus.attendance.team.domain.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Member create(String name, String email, String encodedPassword, MemberRole role, MemberStatus status, Team team) {
        Member member = new Member();
        member.name = name;
        member.email = email;
        member.password = encodedPassword;
        member.role = role;
        member.status = MemberStatus.ACTIVE;
        member.team = team;
        member.createdAt = LocalDateTime.now();
        return member;
    }

    public void deactivate() {
        this.status = MemberStatus.INACTIVE;
    }

    public void activate() {
        this.status = MemberStatus.ACTIVE;
    }

    public void changeRole(MemberRole role) {
        this.role = role;
    }

    public void updateProfile(String name, String encodedPassword, PasswordEncoder passwordEncoder) {
        if (name != null) {
            this.name = name;
        }
        if (encodedPassword != null) {
            this.password = passwordEncoder.encode(encodedPassword);
        }
    }
}
