package com.quantlab.client.dto;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeployReqDto {
    private Long userId;
    private Long strategyId;
    private Long multiplier;
    private Long index;
    private String atmType;
    private Long minCapital;
    private String underlying;
    private String orderId;
    private String executionTypeId;
//    private Integer freshEntryCount;
    private Integer entryHours;
    private Integer entryMinutes;
    private String expiry;
    private List<Long> days = new ArrayList<>();
    private Integer exitHours;
    private Integer exitMinutes;
    private String profitMtmToggle;
    private String profitMtmType;
    private long profitMtmValue;
    private String stoplossToggle;
    private String stoplossType;
    private long stoplossValue;
//    private Long deltaSlippage;

}
