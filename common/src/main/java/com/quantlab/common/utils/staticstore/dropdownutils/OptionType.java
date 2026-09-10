package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum OptionType {
    OPTION("OPTION","Options"),
    FUTURE("FUTURE","Futures");

    private final String key;
    private final String label;

    OptionType(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
