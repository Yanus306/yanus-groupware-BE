package com.yanus.attendance.drive;

import com.yanus.attendance.drive.domain.StorageService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public class FakeStorageService implements StorageService {

    private final Map<String, byte[]> store = new HashMap<>();

    @Override
    public String upload(MultipartFile file, String storedName) {
        try {
            store.put(storedName, file.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return storedName;
    }

    @Override
    public byte[] download(String storedName) {
        return store.getOrDefault(storedName, new byte[0]);
    }

    @Override
    public void delete(String storedName) {
        store.remove(storedName);
    }
}
