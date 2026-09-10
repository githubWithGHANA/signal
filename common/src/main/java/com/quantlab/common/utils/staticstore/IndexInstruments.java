package com.quantlab.common.utils.staticstore;

import com.quantlab.common.utils.staticstore.dropdownutils.ExpiryType;
import lombok.Getter;

@Getter
public enum IndexInstruments {


    NIFTY("NIFTY",26000),
    NIFTY_BANK("BANKNIFTY",26001),
    FIN_NIFTY("FINNIFTY",26034),
    BSE_SENSEX("SENSEX",26065),
    BSE_BANKEX("BANKEX",26118);

    private final String key;
    private final Integer label;

    IndexInstruments(String key, Integer label) {
        this.key = key;
        this.label = label;
    }

    public static IndexInstruments fromKey(String key) {
        for (IndexInstruments type : IndexInstruments.values()) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with key " + key);
    }
}
