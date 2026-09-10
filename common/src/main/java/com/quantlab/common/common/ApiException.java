package com.quantlab.common.common;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiException extends RuntimeException {

    private String errorCode;
    private String details;

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

}

