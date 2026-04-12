package com.yanus.attendance.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String email, String verificationToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[yANUs] 이메일 인증을 완료해주세요");
        message.setText("아래 토큰을 입력하여 이메일 인증을 완료하세요.\n\n토큰: " + verificationToken
                + "\n\n토큰은 30분간 유효합니다.");
        mailSender.send(message);
    }
}
