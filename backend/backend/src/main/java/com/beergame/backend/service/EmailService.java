package com.beergame.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String senderMail;

    public void sendVerificationEmail(String toEmail, int token) {

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(senderMail, "Beer Game");
            helper.setTo(toEmail);
            helper.setSubject("Verification Token for the Beer Game");

            String htmlContent = """
                    <h1>Welcome to Beer Game!</h1>
                    <p>Thank you for registering.</p>
                    <p><b>Your OTP is: %d</b></p>
                    <p>Enter this OTP in the app to verify your email.</p>
                    <br/>
                    <p>If you did not register, please ignore this email.</p>
                    """.formatted(token);

            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("Email sent to {}", toEmail);

        } catch (Exception e) {
            log.error("Error sending mail to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
