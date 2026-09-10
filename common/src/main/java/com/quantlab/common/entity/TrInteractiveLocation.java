package com.quantlab.common.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tr")
@Data
public class TrInteractiveLocation {
    private String location;
    private int port;
}
