package com.quantlab.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClientResponseDTO {
    private String clientId;
    private String clientName;
    private String address;
    private String mobileNumber;
    private String emailId;
    private String secretKey;
    private String appKey;
    private boolean xtsClient;
    @JsonProperty("isCugUser")
    private boolean isCugUser;
    private String trToken;
    @JsonProperty("userSessionId")
    private String userSessionId;
    @JsonProperty("jsessionId")
    private String jsessionId;

    boolean getXtsClient(){
        return xtsClient;
    }
}
