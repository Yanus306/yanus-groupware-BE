package com.yanus.attendance.drive.domain;

import java.util.List;
import java.util.Optional;

public interface DriveFileRepository {

    DriveFile save(DriveFile driveFile);

    Optional<DriveFile> findById(Long id);

    List<DriveFile> findAllByUploadedById(Long uploadedById);

    void deletedById(Long id);
}
