package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsIndexes  {
    Long totalProfit;
    Long maxDrawDown;
    Long maxDrawDownPercent;
    Long winRate;
    Long lossRate;
    Long capitalRequired;
    Long totalTradingDays;
    Long totalTrades;
    Long avgTradesPerDay;
    Long avgDailyProfit;
    Long avgProfitOnWinDays;
    Long avgLossOnLossDays;
}
