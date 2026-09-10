package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum TgtMenu {

    PERCENT_OF_ENTRY_PRICE("PercentOfEntryPrice","Percent of EntryPrice"),
    POINTS("Points","Amount");


    private final String key;
    private final String label;

    TgtMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
