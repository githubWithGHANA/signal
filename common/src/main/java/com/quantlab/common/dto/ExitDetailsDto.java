package com.quantlab.common.dto;

import lombok.Data;

@Data
public class ExitDetailsDto {
    private Integer exitHourTime;
    private Integer exitMinsTime;
    private String targetUnitToggle;
    private String targetUnitType;
    private Long targetUnitValue;
    private String stopLossUnitToggle;
    private String stopLossUnitType;
    private Long stopLossUnitValue;
    private String exitOnExpiryFlag;
    private Integer exitAfterEntryDays;

}
