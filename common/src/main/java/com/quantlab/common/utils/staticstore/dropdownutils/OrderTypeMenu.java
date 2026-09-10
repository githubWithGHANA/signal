package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum OrderTypeMenu {

    BUY("Buy", "Buy"),
    SELL("Sell", "Sell");

    private final String key;
    private final String label;

    OrderTypeMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
