package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ManualExit {
    ENABLED("y", "Enabled"),
    DISABLED("n", "Disabled");

    private final String key;
    private final String label;

    ManualExit(String key, String label) {
        this.key = key;
        this.label = label;
    }
}