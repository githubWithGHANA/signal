package com.quantlab.common.service.emailtemplets;

import com.quantlab.common.emailService.EmailService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BodSuccessMail implements Runnable {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BodSuccessMail.class);
    private  EmailService emailService;

    private String email;
    private String message;

    public BodSuccessMail(EmailService emailService) {
        this.emailService = emailService;
    }

    public void sendSuccessMail(String email, String message) {
        this.email = email.toLowerCase();
        this.message = this.emailService.getEmailSuccessTemplate();
       this.run();
    }


    @Override
    public void run() {
        String subject = "OTP verification";
        Long startTime = Instant.now().toEpochMilli();
        String message = this.emailService.getEmailOtpTemplate(this.message);
//        this.emailService.sendEmail(email, subject, message);
        log.info("OTP template time is  " + (Instant.now().toEpochMilli() - startTime));
    }
}
