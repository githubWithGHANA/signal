package com.quantlab.common.exception;

import lombok.Data;

@Data
public class ErrorDetail {
    private String code;
    private String message;
    private String field; // Optional: could specify which field caused the error

    public ErrorDetail(String code, String message, String field) {
        this.code = code;
        this.message = message;
        this.field = field;
    }
}
