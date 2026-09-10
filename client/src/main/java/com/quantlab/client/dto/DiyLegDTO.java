package com.quantlab.client.dto;

import lombok.Data;

@Data
public class DiyLegDTO {
    private Long legId;
    private String position;
    private String optionType;
    private Long lots;
    private String expiry;
    private String strikeSelection;
    private String strikeSelectionValue;
    private String strikeType;
    private Boolean tgtToogle;
    private String tgtType;
    private Long tgtValue;
    private Boolean stopLossToggle;
    private String stopLossType;
    private Long stopLossValue;
    private Boolean tslToggle;
    private String tslType;
    private Long tslValue;
    private Long tdValue;
    private String derivativeType;
}
