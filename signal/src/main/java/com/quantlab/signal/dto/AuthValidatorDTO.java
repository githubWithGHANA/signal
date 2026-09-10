package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class AuthValidatorDTO {
    String token;
    String otpSessionId;
    String mobileNumber;
    String clientId;
}

