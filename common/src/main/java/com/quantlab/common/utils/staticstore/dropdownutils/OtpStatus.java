package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum OtpStatus {
    VERIFIED("verified","Verified"),
    UNVERIFIED("unverified","Unverified");

    private final String key;
    private final String label;

    OtpStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}