package com.quantlab.common.utils.staticstore;

import lombok.Getter;

@Getter
public enum UserType {
    XTS("XTS", "XTS Protocol"),
    TR("TR", "TR Protocol");

    private final String key;
    private final String label;

    UserType(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
