package com.quantlab.common.utils.staticstore;

import com.quantlab.common.utils.staticstore.dropdownutils.ExpiryType;
import lombok.Getter;

@Getter
public enum IndexDifference {

    NIFTY("NIFTY",50),
    NIFTY_BANK("BANKNIFTY",100),
    FIN_NIFTY("FINNIFTY",50),
    MIDCAP_NIFTY("MIDCAPNIFTY",25),
    BSE_SENSEX("SENSEX",100),
    BSE_BANKEX("BANKEX",100);

    private final String key;
    private final Integer label;

    IndexDifference(String key, Integer label) {
        this.key = key;
        this.label = label;
    }

    public static IndexDifference fromKey(String key) {
        for (IndexDifference type : IndexDifference.values()) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with key " + key);
    }
}
