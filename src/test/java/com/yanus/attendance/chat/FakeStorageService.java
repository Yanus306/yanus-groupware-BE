package com.yanus.attendance.chat;

import com.yanus.attendance.drive.domain.StorageService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public class FakeStorageService implements StorageService {

    private final List<String> uploaded = new ArrayList<>();

    @Override
    public String upload(MultipartFile file, String storedName) {
        uploaded.add(storedName);
        return storedName;
    }

    @Override
    public byte[] download(String fileName) {
        return new byte[0];
    }

    @Override
    public void delete(String fileName) {
        uploaded.remove(fileName);
    }

    public int uploadCount() {
        return uploaded.size();
    }
}
