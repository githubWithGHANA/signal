package com.quantlab.common.exception.custom;

public class StrategyNotFoundException extends RuntimeException {
    public StrategyNotFoundException(String message) {
        super(message);
    }
}
