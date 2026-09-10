package com.quantlab.client.dto;

import lombok.Data;

@Data
public class ExitSingleStrategyDto {
    private Long strategyId;
    private Long signalId;
}
