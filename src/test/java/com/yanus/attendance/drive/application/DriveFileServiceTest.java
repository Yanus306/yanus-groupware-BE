package com.yanus.attendance.drive.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yanus.attendance.drive.FakeDriveFileRepository;
import com.yanus.attendance.drive.FakeStorageService;
import com.yanus.attendance.drive.domain.DriveFileRepository;
import com.yanus.attendance.drive.domain.StorageService;
import com.yanus.attendance.drive.presentation.dto.DriveFileResponse;
import com.yanus.attendance.global.exception.BusinessException;
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

    private Member createMember() {
        Team team = Team.create("1팀");
        ReflectionTestUtils.setField(team, "id", 1L);
        Member member = Member.create("정용태", "jyt6640@naver.com", "password123", MemberRole.ADMIN, MemberStatus.ACTIVE, team);
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
}
