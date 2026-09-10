package com.quantlab.client.dto;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class DiyReqDto {

    private Long strategyId;
    private String strategyName;
    private Long index;
    private Long capital;
    // entry settings
    private String strategyType;
    private List<Long> entryOnDays;
    private String executionTypeId;
    // exit settings
    private Integer entryHours;
    private Integer entryMinutes;
    private Integer exitHours;
    private Integer exitMinutes;
    private String exitOnExpiry;
    private Integer exitAfterEntryDays;
    private String targetMtmToggle;
    private String targetMtmType;
    private Long targetMtmValue;
    private String stopLossMtmToggle;
    private String stopLossMtmType;
    private Long stopLossMtmValue;

    // legs
    private List<DiyLegDTO> legs;
}
