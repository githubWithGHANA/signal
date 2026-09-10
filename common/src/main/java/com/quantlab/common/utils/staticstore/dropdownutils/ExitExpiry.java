package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ExitExpiry {

    PERCENT_OF_ENTRY("yes", "Yes"),
    ENTRY_POINTS("no","No");

    private final String key;
    private final String label;

    ExitExpiry(String key, String label){
        this.key = key;
        this.label = label;
    }

}
