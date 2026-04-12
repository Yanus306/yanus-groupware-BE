package com.yanus.attendance.auth.application;

import com.yanus.attendance.auth.domain.EmailVerificationToken;
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
    private final EmailService emailService;

    public void sendVerification(Long memberId, String email) {
        tokenRepository.deleteByMemberId(memberId);
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.create(token, memberId, LocalDateTime.now().plusMinutes(30)));
        emailService.sendVerificationEmail(email, token);
    }

    public void verify(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (verificationToken.isExpired() || verificationToken.isUsed()) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        Member member = memberRepository.findById(verificationToken.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        verificationToken.use();
        member.activate();
    }
}
