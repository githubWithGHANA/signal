package com.quantlab.signal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegOrderDto {
    private Long id;
    private String name;
    private String instrumentName;
    private Long exchangeInstrumentID;
    private Long exchangeID;
    private Long instrumentID;
    private Long price;
    private Long quantity;
    private String segment;
    private String buySellFlag;
    private String sktName;
    private String legExpName;
    private Instant legLineExpiryDate;
    private Long noOfLots;
    private String targetUnitType;
    private Long targetUnitValue;
    private String targetUnitToggle;
    private Long targetFinalValue;
    private String stopLossUnitToggle;
    private String stopLossUnitType;
    private Long stopLossUnitValue;
    private Long stopLossFinalValue;
    private String status;
    private Long exchangeInstrumentId;
    private String optionType;
    private String multiOrdersFlag;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
    private String deleteIndicator;
    private String legType;
    private Long lotSize;
    private String trailingStopLossToggle;
    private String trailingStopLossType;
    private Long trailingStopLossValue;
    private Long trailingDistance;
}
