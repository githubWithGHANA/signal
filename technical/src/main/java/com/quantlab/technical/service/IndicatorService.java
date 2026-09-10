package com.quantlab.technical.service;

import org.springframework.stereotype.Service;

@Service
public class IndicatorService {


    public <T> T calculateIndicatorData(String indicatorName, T data, int period, String type) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
//
//        if ("EMA".equalsIgnoreCase(indicatorName)) {
//            return (T) calculateEMA((double[]) data, period, type);
//        }
//        // Add more indicators as needed
//        throw new UnsupportedOperationException("Indicator not supported: " + indicatorName);
        return data;
    }
}
