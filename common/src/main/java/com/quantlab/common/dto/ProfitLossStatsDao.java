package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfitLossStatsDao {
    private Long userId;
    private Long minProfit;
    private Long maxLoss;
    private String clientId;
}
