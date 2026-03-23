package com.yanus.attendance.drive.infrastructure;

import com.yanus.attendance.drive.domain.DriveFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriveFileSpringDataRepository extends JpaRepository<DriveFile, Long> {
    List<DriveFile> findAllByUploadedById(Long memberId);
}
