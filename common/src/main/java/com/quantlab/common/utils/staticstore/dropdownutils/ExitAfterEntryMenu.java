package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum ExitAfterEntryMenu {

    EXIT_1(1, "1"),
    EXIT_2(2, "2"),
    EXIT_3(3, "3"),
    EXIT_4(4, "4"),
    EXIT_5(5, "5"),
    EXIT_6(6, "6"),
    EXIT_7(7, "7"),
    EXIT_8(8, "8"),
    EXIT_9(9, "9"),
    EXIT_10(10, "10");

    private final Integer key;
    private final String label;

    ExitAfterEntryMenu(Integer key, String label) {
        this.key = key;
        this.label = label;
    }
}



