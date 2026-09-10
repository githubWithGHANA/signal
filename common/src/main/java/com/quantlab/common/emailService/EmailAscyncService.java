package com.quantlab.common.emailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmailAscyncService implements Runnable {

    @Autowired
    EmailService emailService;


    @Override
    public void run() {

    }
}
