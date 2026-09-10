package com.quantlab.common.utils.staticstore;

import lombok.Getter;

@Getter
public enum DeltaNeutralExit {

    NIFTY("NIFTY", 50,25),
    BANKNIFTY("BANKNIFTY",250,125);

    private final String key;
    private final int positive;
    private final int negative;

    DeltaNeutralExit(String key, int positive, int negative){
        this.key = key;
        this.positive = positive;
        this.negative = negative;
    }

}
