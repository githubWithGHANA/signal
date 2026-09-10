package com.quantlab.signal.dto;

import lombok.Data;

import java.util.List;

@Data
public class XtsPlaceOrderDto {
    private String tenantID;
    private String signalID;
    private String appKey;
    private String secretKey;
    private String token;
    private Boolean exitFlag;
    private Long requiredCapital;
    private List<XtsOrdersDto> orders;
}
