package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum LotSizeMenu {

    LOT_1(1L, "1"),
    LOT_2(2L, "2"),
    LOT_3(3L, "3"),
    LOT_4(4L, "4"),
    LOT_5(5L, "5"),
    LOT_6(6L, "6"),
    LOT_7(7L, "7"),
    LOT_8(8L, "8"),
    LOT_9(9L, "9"),
    LOT_10(10L, "10");

    private final Long key;
    private final String label;

    LotSizeMenu(Long key, String label) {
        this.key = key;
        this.label = label;
    }
}




