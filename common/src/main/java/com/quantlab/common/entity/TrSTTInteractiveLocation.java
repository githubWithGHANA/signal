package com.quantlab.common.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tr-stt")
@Data
public class TrSTTInteractiveLocation {
    private String location;
    private int port;
}
