package com.quantlab.signal.dto.redisDto;

import lombok.Data;

@Data
public class InterActiveTokensDTO {
    private String clientId;
    private String token;
    private String userSessionId;
    private String jSessionId;
    private String number;
    private String otpSessionId;
    private String loginToken;

}
