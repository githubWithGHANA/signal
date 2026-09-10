package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum Segment {
    NSE("NSEFO","NSEFO"),
    BSE("BSEFO","BSEFO");

    private final String key;
    private final String label;

    Segment(String key, String label) {
        this.key = key;
        this.label = label;
    }
}