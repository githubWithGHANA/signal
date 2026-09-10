package com.quantlab.signal.dto;

import lombok.Data;

import java.util.List;
@Data
public class TrPlaceOrderDto {
    private String tenantID;
    private String signalID;
    private String trToken;
    private Boolean cugUser;
    private String userSessionID;
    private String jSessionID;
    private String brokerName;
    private String branchId;
    private Boolean exitFlag;
    private Long requiredCapital;
    private List<TrOrdersDto> orders;
}
