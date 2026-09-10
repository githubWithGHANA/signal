package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum TslMenu {

    PERCENT_OF_ENTRY("PercentOfEntry", "Percent of Entry"),
    ENTRY_POINTS("EntryPoints","Entry Points"),
    PERCENT_OF_SL("PercentOfSl", "Percent of SL"),
    SL_POINTS("SlPoints", "SL Points");

    private final String key;
    private final String label;

    TslMenu(String key, String label){
        this.key = key;
        this.label = label;
    }

}
