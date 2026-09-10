package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum SignalStatus {
    LIVE("live","Live"),
    EXIT("exit","Exit"),
    SCHEDULED_PNL("scheduled_exit_pnl","Scheduled exit PnL");

    private final String key;
    private final String label;

    SignalStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}