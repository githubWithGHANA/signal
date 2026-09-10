package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum AtmType {
    SPOT_ATM("SpotAtm","Spot Atm"),
    FUTURE_ATM("FutureAtm","Future Atm"),
    SYNTHETIC_ATM("SyntheticAtm","Synthetic Atm"),
    PREMIUM_NEAREST("premiumNearest","Premium - Nearest"),
    PREMIUM_GREATERTHAN("premiumGreaterthan","Premium >"),
    PREMIUM_LESSTHAN("premiumLessthan","Premium <"),
    DELTA_NEAREST("deltaNearest","Delta - Nearest"),
    DELTA_GREATERTHAN("deltaGreaterThan","Delta >"),
    DELTA_LESSTHAN("deltaLessThan","Delta <");

    private final String key;
    private final String label;

    AtmType(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
