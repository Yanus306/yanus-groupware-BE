package com.yanus.attendance.chat.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_file_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    static MessageFile create(Message message, String originalName, String storedName,
                              String bucket, Long size, String contentType) {
        MessageFile file = new MessageFile();
        file.message = message;
        file.originalName = originalName;
        file.storedName = storedName;
        file.bucket = bucket;
        file.size = size;
        file.contentType = contentType;
        file.createdAt = LocalDateTime.now();
        return file;
    }
}
