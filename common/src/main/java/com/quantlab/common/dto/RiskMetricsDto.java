package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RiskMetricsDto {
    private Long maxDrawDown;
    private Long sharpeRatio;
    private Long sortinoRatio;
}
