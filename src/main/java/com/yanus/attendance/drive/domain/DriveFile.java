package com.yanus.attendance.drive.domain;

import com.yanus.attendance.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "drive_file")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DriveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "drive_file_id")
    private Long id;

    private String originalName;

    private String storedName;

    private String bucket;

    private Long size;

    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private Member uploadedBy;

    private LocalDateTime createdAt;

    public static DriveFile create(Member uploadedBy, String originalName, String storedName,
                                   String bucket, Long size, String contentType) {
        DriveFile file = new DriveFile();
        file.uploadedBy = uploadedBy;
        file.originalName = originalName;
        file.storedName = storedName;
        file.bucket = bucket;
        file.size = size;
        file.contentType = contentType;
        file.createdAt = LocalDateTime.now();
        return file;
    }
}
