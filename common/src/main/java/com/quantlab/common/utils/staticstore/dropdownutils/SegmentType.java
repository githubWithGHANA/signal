package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum SegmentType {

    CE("Ce","Call"),
    PE("Pe","Put");

    private final String key;
    private final String label;

    SegmentType(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
