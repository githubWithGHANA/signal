package com.quantlab.common.exception.custom;

public class StrategyAlreadyLiveException extends RuntimeException {
    public StrategyAlreadyLiveException(String message) {
        super(message);
    }
}
