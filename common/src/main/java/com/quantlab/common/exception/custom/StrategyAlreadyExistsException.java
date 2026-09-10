package com.quantlab.common.exception.custom;

public class StrategyAlreadyExistsException extends RuntimeException{
    public StrategyAlreadyExistsException(String message) {
        super(message);
    }
}
