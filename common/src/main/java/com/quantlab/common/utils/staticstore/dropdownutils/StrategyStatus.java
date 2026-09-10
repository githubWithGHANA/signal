package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrategyStatus {
    EXITED_MANUALLY("Exited-Manually","Exited-Manually"),
    EXIT("exit","Exit"),
    REJECTED("Rejected","Rejected"),
    COMPLETE("complete","complete"),
    CANCELLED("cancelled","Cancelled");

    private final String key;
    private final String label;

    StrategyStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}