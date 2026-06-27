package com.yanus.attendance.chat.application;

import com.yanus.attendance.chat.domain.DeviceToken;
import com.yanus.attendance.chat.domain.DeviceTokenRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final MemberRepository memberRepository;

    public void register(Long memberId, String token) {
        if (deviceTokenRepository.existsByToken(token)) {
            return;
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        deviceTokenRepository.save(DeviceToken.create(member, token));
    }

    public void unregister(String token) {
        deviceTokenRepository.deleteByToken(token);
    }
}
