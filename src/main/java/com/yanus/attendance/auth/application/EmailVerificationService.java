package com.yanus.attendance.auth.application;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
import com.yanus.attendance.auth.domain.EmailVerificationJobRepository;
import com.yanus.attendance.auth.domain.EmailVerificationTokenRepository;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final MemberRepository memberRepository;
    private final EmailVerificationJobRepository jobs;

    public void enqueueVerification(Long memberId) {
        tokenRepository.deleteByMemberId(memberId);
        var token = EmailVerificationToken.create(UUID.randomUUID().toString(), memberId,
                LocalDateTime.now().plusMinutes(30));
        jobs.enqueue(tokenRepository.save(token).getId());
    }

    public void verify(String token) {
        Long memberId = tokenRepository.findMemberIdByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN));
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (!member.isPending() || verificationToken.isExpired() || verificationToken.isUsed()) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        verificationToken.use();
        member.activate();
    }

    public void resend(String email) {
        Member member = memberRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.isPending()) {
            enqueueVerification(member.getId());
        }
    }
}
