package com.quantlab.common.config;


import com.quantlab.common.common.AppLoginService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppLoginConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppLoginConfig.class);

    @Autowired
    private AppLoginService startupCondition; // Your custom condition check

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        if (!startupCondition.isValidHardware()) {
//            logger.info("validation failed. Stopping the application.");
            SpringApplication.exit(applicationContext, () -> 1);  // Exit with a non-zero status
            // Alternatively: System.exit(1);
        } else {
//            System.out.println("Condition passed. Application starting...");
        }
    }
}
