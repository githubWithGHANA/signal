package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeekStatsSummaryDto {
    private String day;
    private double returns;
    private double maxProfit;
    private double maxLoss;
}
