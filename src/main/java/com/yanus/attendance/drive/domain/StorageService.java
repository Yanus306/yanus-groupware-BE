package com.yanus.attendance.drive.domain;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    String upload(MultipartFile file, String storedName);

    byte[] download(String fileName);

    void delete(String fileName);
}
