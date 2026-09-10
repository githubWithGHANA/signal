package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum LegSide {
    BUY("buy","Buy"),
    SELL("sell","Sell");

    private final String key;
    private final String label;

    LegSide(String key, String label) {
        this.key = key;
        this.label = label;
    }
}