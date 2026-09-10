package com.quantlab.signal.utils;


import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.UserAdmin;
import com.quantlab.common.exception.custom.UserNotFoundException;
import com.quantlab.common.repository.AdminRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.OtpStatus;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;


@Service
public class TestMail implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(TestMail.class);
    private final Random random = new Random();

    @Autowired
    private AdminRepository adminRepository;

    private final EmailService emailService;

    String email;
    String otp;

    public TestMail(EmailService emailService, @Nullable String email, @Nullable String otp) {
        this.emailService = emailService;
        this.email = email;
        this.otp = otp;
    }



    @Transactional
    public void generateOtp(String email) {
        this.email = email.toLowerCase();
        try {
            Optional<UserAdmin> Admin = adminRepository.findByEmail(email);
            if (Admin.isPresent()) {
                String otp = String.format("%06d", random.nextInt(999999));
                UserAdmin userAdmin = Admin.get();
                userAdmin.setEmail(email);
                userAdmin.setOtp(otp);
                userAdmin.setOtpStatus(OtpStatus.UNVERIFIED.getKey());
                // added an exipry of 3 mins
                userAdmin.setOtpExpiry(Instant.now().plus(Duration.ofMinutes(3)));
                adminRepository.save(userAdmin);
                // TODO: Send the OTP via email (implement email sending logic)
                log.info("OTP for " + email + ": " + otp);
                this.otp = otp;
//                // For testing purposes
//                String subject = "OTP verification";
//                Long startTime = Instant.now().toEpochMilli();
//                String message = emailService.getEmailOtpTemplate(otp);
//
//                emailService.sendEmail(email,subject,message);
//                logger.info("OTP template time is  " + (Instant.now().toEpochMilli() - startTime));
                TestMail task = new TestMail(emailService,email,otp);

                // Create a thread and pass the task to it
                Thread thread = new Thread(task);

                // Start the thread
                thread.start();
            }

        } catch (Exception e) {
            log.error("Error generating OTP for email : "+ email, e);
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public Boolean validateOtp(String email, String otp) {
        try {
            Optional<UserAdmin> userAdminOptional = adminRepository.findByEmail(email);
            if (userAdminOptional.isEmpty()){
                log.warn("User not found for email: {}", email);
                throw new UserNotFoundException("User not found for provided email");
            }
            UserAdmin userAdmin = userAdminOptional.get();

            if (userAdmin.getOtpExpiry() != null && Instant.now().isAfter(userAdmin.getOtpExpiry())) {
                log.warn("OTP expired for email: {}", email);
                throw new IllegalStateException("OTP has expired. Please request a new one.");
            }

            boolean isOtpValid = userAdmin.getOtp().equals(otp);
            userAdmin.setOtpStatus(isOtpValid ? OtpStatus.VERIFIED.getKey() : OtpStatus.UNVERIFIED.getKey());
            adminRepository.save(userAdmin);

            return isOtpValid;
        } catch (UserNotFoundException | IllegalStateException e) {
            log.error("Validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error validating OTP for email: {}", email, e);
            throw new RuntimeException("An unexpected error occurred while validating OTP.", e);
        }
    }

    @Override
    public void run() {
        String subject = "BOD verification";
        Long startTime = Instant.now().toEpochMilli();
        String message = this.emailService.getEmailCriticalErrorBodTemplate(this.otp);
//        this.emailService.sendEmail(email, subject, message);


        log.info("OTP template time is  " + (Instant.now().toEpochMilli() - startTime));
//        String nmessage = this.emailService.getEmailSuccessBodTemplate(this.otp);
//        this.emailService.sendEmail(email, subject, nmessage);
        log.info("BOD template time is  " + (Instant.now().toEpochMilli() - startTime));
    }
}
