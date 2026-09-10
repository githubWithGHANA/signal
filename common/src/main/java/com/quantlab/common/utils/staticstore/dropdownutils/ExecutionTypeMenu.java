package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ExecutionTypeMenu {
    LIVE_TRADING("LiveTrading","Live Trading"),
    PAPER_TRADING("PaperTrading","Forward Test");


    private final String key;
    private final String label;

    ExecutionTypeMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static ExecutionTypeMenu fromKey(String key) {
        for (ExecutionTypeMenu type : ExecutionTypeMenu.values()) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with key " + key);
    }
}
