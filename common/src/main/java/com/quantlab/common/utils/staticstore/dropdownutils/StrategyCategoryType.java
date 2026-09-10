package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrategyCategoryType {
    DIY("diy", "DIY"),
    PREBUILT("prebuilt", "PreBuilt"),
    INHOUSE("inhouse", "In-house");

    private final String key;
    private final String label;

    StrategyCategoryType(String key, String label) {
        this.key = key;
        this.label = label;
    }

}