
package com.quantlab.common.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsResponseDto<T> {
    PerformanceOverviewDto performanceOverview;
    ProfitStatisticsDto ProfitStatistics;
    RiskMetricsDto RiskMetrics;
    List<StatisticsResponseObjDto<T>> statistics;
    List<MontlyStatisticsDto> monthlyStatistics;
    List<WeekStatsSummaryDto> weekStatsSummary;
    StatisticsIndexes statisticsIndexes;
}
