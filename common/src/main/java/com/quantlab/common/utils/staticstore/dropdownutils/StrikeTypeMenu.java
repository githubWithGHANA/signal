package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum StrikeTypeMenu {

    ITM_20("ITM20", "ITM 20"),
    ITM_19("ITM19", "ITM 19"),
    ITM_18("ITM18", "ITM 18"),
    ITM_17("ITM17", "ITM 17"),
    ITM_16("ITM16", "ITM 16"),
    ITM_15("ITM15", "ITM 15"),
    ITM_14("ITM14", "ITM 14"),
    ITM_13("ITM13", "ITM 13"),
    ITM_12("ITM12", "ITM 12"),
    ITM_11("ITM11", "ITM 11"),
    ITM_10("ITM10", "ITM 10"),
    ITM_9("ITM9", "ITM 9"),
    ITM_8("ITM8", "ITM 8"),
    ITM_7("ITM7", "ITM 7"),
    ITM_6("ITM6", "ITM 6"),
    ITM_5("ITM5", "ITM 5"),
    ITM_4("ITM4", "ITM 4"),
    ITM_3("ITM3", "ITM 3"),
    ITM_2("ITM2", "ITM 2"),
    ITM_1("ITM1", "ITM 1"),
    ATM("ATM", "ATM"),
    OTM_1("OTM1", "OTM 1"),
    OTM_2("OTM2", "OTM 2"),
    OTM_3("OTM3", "OTM 3"),
    OTM_4("OTM4", "OTM 4"),
    OTM_5("OTM5", "OTM 5"),
    OTM_6("OTM6", "OTM 6"),
    OTM_7("OTM7", "OTM 7"),
    OTM_8("OTM8", "OTM 8"),
    OTM_9("OTM9", "OTM 9"),
    OTM_10("OTM10", "OTM 10"),
    OTM_11("OTM11", "OTM 11"),
    OTM_12("OTM12", "OTM 12"),
    OTM_13("OTM13", "OTM 13"),
    OTM_14("OTM14", "OTM 14"),
    OTM_15("OTM15", "OTM 15"),
    OTM_16("OTM16", "OTM 16"),
    OTM_17("OTM17", "OTM 17"),
    OTM_18("OTM18", "OTM 18"),
    OTM_19("OTM19", "OTM 19"),
    OTM_20("OTM20", "OTM 20");

    private final String key;
    private final String label;

    StrikeTypeMenu(String key, String label) {
        this.key = key;
        this.label = label;
    }
}



