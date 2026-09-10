package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum LegType {
    CALL("call","Call"),
    PUT("put","Put"),
    OPEN("open","Open"),
    EXIT("exit","Exit");

    private final String key;
    private final String label;

    LegType(String key, String label) {
        this.key = key;
        this.label = label;
    }
}