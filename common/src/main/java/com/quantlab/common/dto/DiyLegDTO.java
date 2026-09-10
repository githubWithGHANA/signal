package com.quantlab.common.dto;

import lombok.Data;

@Data
public class DiyLegDTO {
    private Long legId;
    private String position;
    private String optionType;
    private Long lots;
    private String expiry;
    private String strikeSelection;
    private String strikeType;
    private Boolean tgtToogle;
    private String tgtType;
    private Long tgtValue;
    private Boolean stopLossToggle;
    private String stopLossType;
    private Long stopLossValue;
}
