package com.yanus.attendance.drive.application;

import com.yanus.attendance.drive.domain.DriveFile;
import com.yanus.attendance.drive.domain.DriveFileRepository;
import com.yanus.attendance.drive.domain.StorageService;
import com.yanus.attendance.drive.presentation.dto.DriveFileResponse;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class DriveFileService {

    private final DriveFileRepository driveFileRepository;
    private final StorageService storageService;
    private final MemberRepository memberRepository;

    @Value("${minio.bucket}")
    private String bucket;

    public DriveFileResponse upload(Long memberId, MultipartFile file) {
        Member member = findMember(memberId);
        validateDriveAccess(member);
        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        storageService.upload(file, storedName);
        DriveFile driveFile = DriveFile.create(member, file.getOriginalFilename(), storedName,
                bucket, file.getSize(), file.getContentType());
        driveFileRepository.save(driveFile);
        return DriveFileResponse.from(driveFile);
    }

    @Transactional(readOnly = true)
    public List<DriveFileResponse> getMyFiles(Long memberId) {
        return driveFileRepository.findAllByUploadedById(memberId).stream()
                .map(DriveFileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] download(Long actorId, Long fileId) {
        validateDriveAccess(findMember(actorId));
        DriveFile file = findFile(fileId);
        return storageService.download(file.getStoredName());
    }

    public void delete(Long actorId, Long fileId) {
        DriveFile file = findFile(fileId);
        validateDriveAccess(findMember(actorId));
        storageService.delete(file.getStoredName());
        driveFileRepository.deleteById(fileId);
    }

    @Transactional(readOnly = true)
    public List<DriveFileResponse> getAllFiles(Long actorId) {
        validateDriveAccess(findMember(actorId));
        return driveFileRepository.findAll().stream()
                .map(DriveFileResponse::from)
                .toList();
    }

    private DriveFile findFile(Long fileId) {
        return driveFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRIVE_FILE_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateDriveAccess(Member actor) {
        if (actor.getTeam().getName().equals("신입")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
