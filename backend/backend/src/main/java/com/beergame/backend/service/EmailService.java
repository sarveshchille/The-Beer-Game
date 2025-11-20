package com.beergame.backend.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from}")
    private String fromEmail;

    public void sendVerificationEmail(String toEmail, int token) {

        try {
            Resend resend = new Resend(apiKey);

            String html = """
                        <h1>Welcome to Beer Game!</h1>
                        <p>Your verification code is: <b>%d</b></p>
                        <p>Enter this OTP to verify your account.</p>
                    """.formatted(token);

            CreateEmailOptions params = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail)
                    .subject("Beer Game Email Verification")
                    .html(html)
                    .build();

            CreateEmailResponse response = resend.emails().send(params);

            log.info("Email sent successfully: {}", response.getId());

        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage(), e);
        }
    }
}
