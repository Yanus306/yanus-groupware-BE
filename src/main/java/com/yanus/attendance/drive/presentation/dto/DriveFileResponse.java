package com.yanus.attendance.drive.presentation.dto;

import com.yanus.attendance.drive.domain.DriveFile;
import java.time.LocalDateTime;

public record DriveFileResponse(
        Long id,
        String originalName,
        Long size,
        String contentType,
        Long uploadedById,
        String uploadedByName,
        LocalDateTime createdAt
) {
    public static DriveFileResponse from(DriveFile file) {
        return new DriveFileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getSize(),
                file.getContentType(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getName(),
                file.getCreatedAt()
        );
    }
}
