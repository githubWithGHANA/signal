package com.quantlab.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AggregatedStrategyRow {
    private final Long strategyId;
    private final String strategyName;
    private final Long pnlSum;
}
