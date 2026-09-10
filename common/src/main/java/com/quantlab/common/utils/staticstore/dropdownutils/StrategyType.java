package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrategyType {
    INTRADAY("Intraday", "Intraday"),
    POSITIONAL("Positional", "Positional");

    private final String key;
    private final String label;


    StrategyType(String key, String label) {
        this.key = key;
        this.label = label;
    }

}
