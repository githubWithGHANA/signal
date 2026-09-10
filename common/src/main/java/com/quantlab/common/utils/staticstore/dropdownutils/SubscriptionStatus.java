package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum SubscriptionStatus {
    START("Y", "Subscribed"),
    END("N", "Unsubscribed");

    private final String key;
    private final String label;

    SubscriptionStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}