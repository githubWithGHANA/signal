
package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ApiStatus {
    SUCCESS("success", "Success"),
    ERROR("error", "Error");

    private final String key;
    private final String label;

    ApiStatus(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
