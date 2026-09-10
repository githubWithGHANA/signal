package com.quantlab.signal.utils;

import com.quantlab.common.emailService.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EmailImplementation {

    @Autowired
    EmailService emailService;

    public void sendBodSuccessEmail(String message) {
        // Implementation for sending success email
        System.out.println("Success Email Sent: " + message);

    }
}
