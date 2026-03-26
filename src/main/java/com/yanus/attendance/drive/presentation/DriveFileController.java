package com.yanus.attendance.drive.presentation;

import com.yanus.attendance.drive.application.DriveFileService;
import com.yanus.attendance.drive.presentation.dto.DriveFileResponse;
import com.yanus.attendance.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "드라이브", description = "파일 로드, 가져오기, 다운로드, 삭제")
@RestController
@RequestMapping("/api/v1/drive")
@RequiredArgsConstructor
public class DriveFileController {

    private final DriveFileService driveFileService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<DriveFileResponse>> upload(
            @AuthenticationPrincipal Long memberId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(driveFileService.upload(memberId, file)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriveFileResponse>>> getMyFiles(
            @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(driveFileService.getMyFiles(memberId)));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long fileId) {
        byte[] data = driveFileService.download(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long fileId) {
        driveFileService.delete(fileId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DriveFileResponse>>> getAllFiles() {
        return ResponseEntity.ok(ApiResponse.success(driveFileService.getAllFiles()));
    }
}
