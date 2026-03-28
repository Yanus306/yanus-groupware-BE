package com.yanus.attendance.drive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.drive.FakeDriveFileRepository;
import com.yanus.attendance.drive.FakeStorageService;
import com.yanus.attendance.drive.domain.DriveFileRepository;
import com.yanus.attendance.drive.domain.StorageService;
import com.yanus.attendance.drive.presentation.dto.DriveFileResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.FakeMemberRepository;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import com.yanus.attendance.member.domain.MemberStatus;
import com.yanus.attendance.team.domain.Team;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

public class DriveFileServiceTest {

    private DriveFileService driveFileService;
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        DriveFileRepository driveFileRepository = new FakeDriveFileRepository();
        StorageService storageService = new FakeStorageService();
        memberRepository = new FakeMemberRepository();
        driveFileService = new DriveFileService(driveFileRepository, storageService, memberRepository);
    }

    private Member createMember(String teamName, MemberRole role) {
        Team team = Team.create(teamName);
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("테스터", role.name() + "@yanus.com", "password123", role, MemberStatus.ACTIVE, team);
        return memberRepository.save(member);
    }

    @Test
    @DisplayName("파일 업로드")
    void upload_file() {
        // given
        Member member = createMember();
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF 내용".getBytes());

        // when
        DriveFileResponse response = driveFileService.upload(member.getId(), mockFile);

        // then
        assertThat(response.originalName()).isEqualTo("report.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("내 파일 목록 조회")
    void get_my_files() {
        // given
        Member member = createMember();
        MockMultipartFile file1 = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "b.png", "image/png", "b".getBytes());
        driveFileService.upload(member.getId(), file1);
        driveFileService.upload(member.getId(), file2);

        // when
        List<DriveFileResponse> responses = driveFileService.getMyFiles(member.getId());

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("파일 삭제 후 조회 시 예외 발생")
    void delete_file() {
        // given
        Member member = createMember();
        MockMultipartFile mockFile = new MockMultipartFile("file", "report.pdf", "application/pdf", "data".getBytes());
        DriveFileResponse uploaded = driveFileService.upload(member.getId(), mockFile);

        // when
        driveFileService.delete(uploaded.id());

        // then
        assertThatThrownBy(() -> driveFileService.download(uploaded.id()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("전체 파일 목록 조회 테스트")
    void get_all_files() {
        // given
        Member member = createMember();
        MockMultipartFile file1 = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "b.png", "image/png", "b".getBytes());
        driveFileService.upload(member.getId(), file1);
        driveFileService.upload(member.getId(), file2);

        // when
        List<DriveFileResponse> responses = driveFileService.getAllFiles();

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("신입팀 멤버가 파일 업로드 시 예외 발생")
    void junior_team_member_upload_forbidden() {
        // given
        Member member = createMember("신입", MemberRole.MEMBER);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());

        // when & then
        assertThatThrownBy(() -> driveFileService.upload(member.getId(), file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("신입팀 멤버가 파일 다운로드 시 예외 발생")
    void junior_team_member_download_forbidden() {
        // given
        Member admin = createMember("개발팀", MemberRole.ADMIN);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());
        DriveFileResponse uploaded = driveFileService.upload(admin.getId(), file);
        Member member = createMember("신입", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> driveFileService.download(member.getId(), uploaded.id()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("신입팀 멤버가 파일 삭제 시 예외 발생")
    void junior_team_member_delete_forbidden() {
        // given
        Member admin = createMember("개발팀", MemberRole.ADMIN);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());
        DriveFileResponse uploaded = driveFileService.upload(admin.getId(), file);
        Member member = createMember("신입", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> driveFileService.delete(member.getId(), uploaded.id()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("신입팀 멤버가 전체 파일 목록 조회 시 예외 발생")
    void junior_team_member_get_all_files_forbidden() {
        // given
        Member member = createMember("신입", MemberRole.MEMBER);

        // when & then
        assertThatThrownBy(() -> driveFileService.getAllFiles(member.getId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("신입팀이 아닌 멤버는 파일 업로드 성공")
    void non_junior_team_member_upload_success() {
        // given
        Member member = createMember("개발팀", MemberRole.MEMBER);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes());

        // when
        DriveFileResponse response = driveFileService.upload(member.getId(), file);

        // then
        assertThat(response.originalName()).isEqualTo("a.pdf");
    }
}
