package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

import static com.quantlab.common.utils.staticstore.AppConstants.PERCENTOFCAPITAL;

@Getter
public enum MtmMenu {

    PERCENT_OF_CAPITAL(PERCENTOFCAPITAL,"% of Capital"),
    AMOUNT("Amount","Amount");

    private final String key;
    private final String label;

    MtmMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
