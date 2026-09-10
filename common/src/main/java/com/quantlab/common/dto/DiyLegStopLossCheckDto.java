package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DiyLegStopLossCheckDto {
    private Long id;
    private String name;
    private String status;
    private String trailingStopLossToggle;
    private Long trailingDistance;
    private Long trailingStopLossPoints;
    private String stopLossUnitType;
    private String stopLossUnitToggle;
    private Long stopLossUnitValue;
    private Long stopLossFinalValue;
    private String targetUnitType;
    private String targetUnitToggle;
    private Long targetUnitValue;
    private Long targetFinalValue;
    private Long executedPrice;
    private Long exchangeInstrumentId;
    private Long quantity;
    private String buySellFlag;
}
