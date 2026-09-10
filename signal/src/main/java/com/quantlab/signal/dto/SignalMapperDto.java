package com.quantlab.signal.dto;

import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.web.dto.MarketLiveDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignalMapperDto {
    private String id;
    private MarketLiveDto marketLiveDto;
    private MarketData touchlineBinaryResponse;
    private MasterResponseFO masterData;
    private String buySellFlag;
    private String category;
    private String segment;
    private int quantity;
    private Long lots;
    private String positionType;
    private String legType;
    private String name;
    private String optionType;
    private String legName;
    private String derivativeType;
    private String targetUnitType;
    private String stopLossUnitType;
    private Long targetUnitValue;
    private String targetUnitToggle;
    private String stopLossUnitToggle;
    private Long stopLossUnitValue;
    private String trailingStopLossToggle;
    private String trailingStopLossType;
    private Long trailingStopLossValue;
    private Long trailingDistance;
    private Integer signalCount;
    private String exchangeStatus;
}
