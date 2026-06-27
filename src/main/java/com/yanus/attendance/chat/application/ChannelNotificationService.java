package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.Channel;
import com.yanus.attendance.chat.domain.ChannelMute;
import com.yanus.attendance.chat.domain.ChannelMuteRepository;
import com.yanus.attendance.chat.domain.ChannelRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelNotificationService {

    private final ChannelMuteRepository channelMuteRepository;
    private final ChannelRepository channelRepository;
    private final MemberRepository memberRepository;

    public List<Long> getMutedChannelIds(Long memberId) {
        return channelMuteRepository.findChannelIdsByMemberId(memberId);
    }

    public boolean isMuted(Long memberId, Long channelId) {
        return channelMuteRepository.existsByMemberIdAndChannelId(memberId, channelId);
    }

    @Transactional
    public void setMuted(Long memberId, Long channelId, boolean muted) {
        Channel channel = findChannel(channelId);
        Member member = findMember(memberId);
        boolean alreadyMuted = channelMuteRepository.existsByMemberIdAndChannelId(memberId, channelId);
        if (muted && !alreadyMuted) {
            channelMuteRepository.save(ChannelMute.create(member, channel));
        } else if (!muted && alreadyMuted) {
            channelMuteRepository.deleteByMemberIdAndChannelId(memberId, channelId);
        }
    }

    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
