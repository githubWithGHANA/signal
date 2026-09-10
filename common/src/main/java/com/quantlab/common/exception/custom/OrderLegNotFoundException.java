package com.quantlab.common.exception.custom;

public class OrderLegNotFoundException extends RuntimeException {
    public OrderLegNotFoundException(String message) {
        super(message);
    }
}
