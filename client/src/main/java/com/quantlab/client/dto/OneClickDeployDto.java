package com.quantlab.client.dto;

import lombok.Data;

@Data
public class OneClickDeployDto {
    private Long strategyId;
    private Long multiplier;
    private String executionTypeId;
}
