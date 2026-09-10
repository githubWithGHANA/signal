package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceOverviewDto {
    private Long winRatio;
    private Long winDays;
    private Long lossDays;
}
