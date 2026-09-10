package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrategyOption {
    ENABLE_HOLD("y", "Hold Enabled"),
    DISABLE_HOLD("n", "Hold Disabled"),;


    private final String key;
    private final String label;

    StrategyOption(String key, String label) {
        this.key = key;
        this.label = label;
    }
}