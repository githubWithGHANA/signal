package com.quantlab.common.config;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Configuration;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {
    private static final Logger logger = LogManager.getLogger(TimeZoneConfig.class);

    @PostConstruct
    public void setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        logger.info("Application timezone set to IST (Asia/Kolkata)");
    }
}
