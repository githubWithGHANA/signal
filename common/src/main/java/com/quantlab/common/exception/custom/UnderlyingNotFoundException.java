package com.quantlab.common.exception.custom;

public class UnderlyingNotFoundException extends RuntimeException {
    public UnderlyingNotFoundException(String message) {
        super(message);
    }
}
