package com.quantlab.common.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "xts")
@Data
public class XtsInteractiveLocation {
    private String location;
    private int port;
}
