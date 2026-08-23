package com.yanus.attendance.drive.presentation.dto;

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
    public static DriveFileResponse from(com.yanus.attendance.drive.application.dto.DriveFileResponse response) {
        return new DriveFileResponse(
                response.id(),
                response.originalName(),
                response.size(),
                response.contentType(),
                response.uploadedById(),
                response.uploadedByName(),
                response.createdAt()
        );
    }
}
