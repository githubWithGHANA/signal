package com.quantlab.common.dao;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "endpoint")
public class EndpointProperties {
    private String validate;
    private String profile;
    private String appID;
    private String appKey;
    private String xtsUrl;
}