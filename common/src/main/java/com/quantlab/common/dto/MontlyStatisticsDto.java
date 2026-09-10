
package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MontlyStatisticsDto {
    private String month;
    private int totalTrades;
    private BigDecimal pnlRs;
    private BigDecimal pnlPercent;
}
