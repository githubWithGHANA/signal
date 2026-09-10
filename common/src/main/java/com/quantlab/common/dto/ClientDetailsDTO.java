package com.quantlab.common.dto;

import lombok.Data;

@Data
public class ClientDetailsDTO {
    private String clientId;
    private String clientName;
    private String address;
    private String mobileNo;
    private String emailId;
    private boolean xtsClient;
    private String xTSSecretKey;
    private String xTSAppKey;
    private boolean cugUser;
    private String trToken;
    private String userSessionId;
    private String jsessionId;
    private String userId;
    private String appKey;
    private String secretKey;
    private Double minProfit;
    private Double maxLoss;
    public boolean getXtsClient(){
        return xtsClient;
    }

}