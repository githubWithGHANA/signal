package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum TradingMode {
    LIVE("live", "Live"),
    FORWARD("forward", "Forward");


    private final String key;
    private final String label;

    TradingMode(String key, String label) {
        this.key = key;
        this.label = label;
    }
}