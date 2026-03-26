package com.yanus.attendance.drive;

import com.yanus.attendance.drive.domain.DriveFile;
import com.yanus.attendance.drive.domain.DriveFileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

public class FakeDriveFileRepository implements DriveFileRepository {

    private final Map<Long, DriveFile> store = new HashMap<>();
    private Long sequence = 1L;

    @Override
    public DriveFile save(DriveFile driveFile) {
        if (driveFile.getId() == null) {
            ReflectionTestUtils.setField(driveFile, "id", sequence++);
        }
        store.put(driveFile.getId(), driveFile);
        return driveFile;
    }

    @Override
    public Optional<DriveFile> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DriveFile> findAllByUploadedById(Long memberId) {
        return store.values().stream()
                .filter(f -> f.getUploadedBy().getId().equals(memberId))
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public List<DriveFile> findAll() {
        return new ArrayList<>(store.values());
    }
}
