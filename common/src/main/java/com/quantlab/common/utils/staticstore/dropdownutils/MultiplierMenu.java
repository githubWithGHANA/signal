package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum MultiplierMenu {

    ONE(1L,"1x"),
    TWO(2L,"2x"),
    THREE(3L,"3x"),
    FOUR(4L,"4x"),
    FIVE(5L,"5x");

    private final Long key;
    private final String label;

    MultiplierMenu(Long key, String label) {
        this.key = key;
        this.label = label;
    }

    public static MultiplierMenu fromKey(Long key) {
        for (MultiplierMenu type : MultiplierMenu.values()) {
            if (type.getKey().equals(key)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with key " + key);
    }

}
