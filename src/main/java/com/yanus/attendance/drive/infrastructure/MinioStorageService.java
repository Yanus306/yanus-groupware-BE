package com.yanus.attendance.drive.infrastructure;

import com.yanus.attendance.drive.domain.StorageService;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    public String upload(MultipartFile file, String storedName) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storedName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return storedName;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public byte[] download(String storedName) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storedName)
                    .build());
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DRIVE_FILE_NOT_FOUND);
        }
    }

    @Override
    public void delete(String storedName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storedName)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.DRIVE_FILE_NOT_FOUND);
        }
    }
}
