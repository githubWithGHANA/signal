package com.quantlab.common.dto;

import lombok.Data;

@Data
public class StrategyLegDto {

    private Long id;

    // buySellFlag column from strategy Leg
    private String positions;

    // optionType column from strategy Leg
    private String optionType;

    // noOfLots column from strategy Leg
    private Long lots;

    // legExpName column from strategy Leg
    private String expiry;

    // sktName column from strategy Leg
    private String strikeType;

    // sktSelection column from strategy Leg
    private String strikeSelection;

    // targetUnitToggle column from strategy Leg
    private String tgtToggle;

    // targetUnitType column from strategy Leg
    private String tgtType;

    // targetUnitValue column from strategy Leg
    private String tgtValue;

    // stopLossUnitToggle column from strategy Leg
    private String stopLossToggle;

    // stopLossUnitType column from strategy Leg
    private String stopLossType;

    // stopLossUnitValue column from strategy Leg
    private String stopLossValue;

}
