package com.quantlab.common.dao;

import lombok.Data;

@Data
public class DeltaNeutralLegDao {
    private String legType;
    private String optionType;
    private Long noOfLots;

    public DeltaNeutralLegDao(String legType, String optionType, Long noOfLots) {
        this.legType = legType;
        this.optionType = optionType;
        this.noOfLots = noOfLots;
    }

}

