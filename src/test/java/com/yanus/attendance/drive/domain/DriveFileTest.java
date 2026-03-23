package com.yanus.attendance.drive.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import com.yanus.attendance.team.domain.TeamName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DriveFileTest {

    private Member createMember() {
        Team team = Team.create(TeamName.BACKEND);
        return Member.create("정용태", "jyt6640@naver.com", "password", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
    }

    @Test
    @DisplayName("DriveFile 생성 시 모든 필드 정상 저장")
    void create_drive_file() {
        // given
        Member member = createMember();

        // when
        DriveFile file = DriveFile.create(member, "report.pdf", "uuid-report.pdf", "yanus-drive", 1024L, "application/pdf");

        // then
        assertThat(file.getOriginalName()).isEqualTo("report.pdf");
        assertThat(file.getStoredName()).isEqualTo("uuid-report.pdf");
        assertThat(file.getSize()).isEqualTo(1024L);
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        assertThat(file.getUploadedBy()).isEqualTo(member);
        assertThat(file.getCreatedAt()).isNotNull();
    }
}
