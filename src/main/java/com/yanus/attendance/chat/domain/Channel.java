package com.yanus.attendance.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "channel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChannelType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Channel create(String name, ChannelType type) {
        Channel channel = new Channel();
        channel.name = name;
        channel.type = type;
        channel.createdAt = LocalDateTime.now();
        return channel;
    }
}
