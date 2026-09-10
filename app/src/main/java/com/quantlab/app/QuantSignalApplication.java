package com.quantlab.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@SpringBootApplication(scanBasePackages = {"com.quantlab"})
@ComponentScan(basePackages = {"com.quantlab.signal","com.quantlab.common", "com.quantlab.client", "com.quantlab.technical" })
@EnableJpaRepositories(basePackages = "com.quantlab.common")
@EntityScan(basePackages = "com.quantlab.common.entity")    
@EnableScheduling
@EnableAsync
@EnableWebSocket
public class QuantSignalApplication {
    public static Logger logger = LoggerFactory.getLogger(QuantSignalApplication.class);

    public static void main(String[] args) {
        logger.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ Redis DB 02 $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
        SpringApplication.run(QuantSignalApplication.class, args);
    }
}
