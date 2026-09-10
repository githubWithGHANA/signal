package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum LegExchangeStatus {
    LIVE("live", "Live"),
    EXIT("exit", "Exit"),
    CREATED("leg-Created", "Leg Created"),
    INTERACTIVE_PLACED("interactive-placed", "Interactive Placed"),
    ERROR_PLACING_ORDER("error-placing-order", "Error Placing Order"),
    PENDING("pending", "Pending"),
    MANUALLY_TRADED("manually-traded", "Manually-Traded"),
    TYPE_OPEN("placed", "placed");

    private final String key;
    private final String label;

    LegExchangeStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}