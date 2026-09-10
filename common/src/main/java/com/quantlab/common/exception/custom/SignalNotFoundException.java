package com.quantlab.common.exception.custom;

public class SignalNotFoundException extends RuntimeException {
    public SignalNotFoundException(String message) {
        super(message);
    }
}
