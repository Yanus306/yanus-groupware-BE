package com.yanus.attendance.auth.application;

import com.yanus.attendance.auth.domain.RefreshToken;
import com.yanus.attendance.auth.domain.RefreshTokenRepository;
import com.yanus.attendance.auth.infrastructure.JwtTokenProvider;
import com.yanus.attendance.auth.presentation.dto.LoginRequest;
import com.yanus.attendance.auth.presentation.dto.LoginResponse;
import com.yanus.attendance.auth.presentation.dto.MeResponse;
import com.yanus.attendance.auth.presentation.dto.RefreshRequest;
import com.yanus.attendance.auth.presentation.dto.RegisterRequest;
import com.yanus.attendance.global.exception.BusinessException;
import com.yanus.attendance.global.exception.ErrorCode;
import com.yanus.attendance.member.domain.Member;
import com.yanus.attendance.member.domain.MemberRepository;
import com.yanus.attendance.member.domain.MemberRole;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Member member = Member.create(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                MemberRole.MEMBER,
                request.team()
        );
        memberRepository.save(member);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshTokenValue = jwtTokenProvider.createRefreshToken(member.getId());

        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(RefreshToken.create(
                refreshTokenValue,
                member.getId(),
                LocalDateTime.now().plusDays(7)
        ));

        return new LoginResponse(accessToken, refreshTokenValue, "Bearer");
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (refreshToken.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        Member member = memberRepository.findById(refreshToken.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());

        return new LoginResponse(newAccessToken, request.refreshToken(), "Bearer");
    }

    @Transactional
    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    public MeResponse me(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MeResponse.from(member);
    }
}
