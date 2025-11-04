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

    public void sendVerificationEmail(String toEmail,int token){

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try{
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage,true,"UTF-8");

            helper.setFrom(senderMail);
            helper.setTo(toEmail);
            helper.setSubject("Verification Token for the Beer Game");

            String htmlContent = "<h1>Welcome to Beer Game!</h1>"
                            + "<p>Thank you for registering. Your Verification Token is as follows</p>"
                           + "<p><a href=\"" + token + "\">Verify My Email</a></p>"
                            + "<p>If you did not register, please ignore this email.</p>";

            helper.setText(htmlContent,true);  
            javaMailSender.send(mimeMessage);
            
            log.info("Email Sent to Player");
        }
        catch(Exception e){

            log.error("Error sending mail to the player");
        }
    }

}
