package com.yanus.attendance.drive.infrastructure;

import com.yanus.attendance.drive.domain.DriveFile;
import com.yanus.attendance.drive.domain.DriveFileRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DriveFileJpaRepository implements DriveFileRepository {

    private final DriveFileSpringDataRepository repository;

    @Override
    public DriveFile save(DriveFile driveFile) {
        return repository.save(driveFile);
    }

    @Override
    public Optional<DriveFile> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<DriveFile> findAllByUploadedById(Long memberId) {
        return repository.findAllByUploadedById(memberId);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
