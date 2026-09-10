package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ExpiryType {

    CURRENT_WEEK("CurrentWeek","Current Week"),
    NEXT_WEEK("NextWeek","Next Week"),
    CURRENT_MONTH("CurrentMonth","Current Month"),
    NEXT_MONTH("NextMonth","Next Month");

    private final String key;
    private final String label;

    ExpiryType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public static ExpiryType fromKey(String key) {
        for (ExpiryType type : ExpiryType.values()) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with key " + key);
    }

}
