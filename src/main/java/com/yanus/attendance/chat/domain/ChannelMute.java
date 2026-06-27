package com.yanus.attendance.chat.domain;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 멤버가 특정 채널의 알림을 끈 상태를 나타낸다. (행이 존재하면 음소거)
 */
@Entity
@Table(name = "channel_mute", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "channel_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelMute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_mute_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ChannelMute create(Member member, Channel channel) {
        ChannelMute mute = new ChannelMute();
        mute.member = member;
        mute.channel = channel;
        mute.createdAt = LocalDateTime.now();
        return mute;
    }
}
