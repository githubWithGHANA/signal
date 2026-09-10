package com.quantlab.client.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegorderDto {
    private Long id;
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
    private String targetUnit;
    private Long targetUnitValue;
    private String stopLossUnit;
    private Long stopLossUnitValue;
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
}
