package com.quantlab.signal.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CookieDTO {
    @NotBlank(message = "token is required")
    private String token;
    @NotBlank(message = "otpSessionId is required")
    private String otpSessionId;
    @NotBlank(message = "mobileNumber is required")
    private String mobileNumber;
    @NotBlank(message = "clientId is required")
    private String clientId;
    private String userId;
    @Override
    public String toString() {
        return "AuthDataDTO{" +
                "token='" + token + '\'' +
                ", otpSessionId='" + otpSessionId + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                ", clientId='" + clientId + '\'' +
                ", userId=" + userId +
                '}';
    }
}
