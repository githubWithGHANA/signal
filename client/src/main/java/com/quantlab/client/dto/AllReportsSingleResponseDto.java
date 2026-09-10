package com.quantlab.client.dto;

import lombok.Data;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Data
public class AllReportsSingleResponseDto {

    private Long id;
    private String strategyName;
    private Double pnl;

    public AllReportsSingleResponseDto(Long id, String strategyName, Long pnl) {
        this.id = id;
        this.strategyName = strategyName;
        this.pnl = (pnl != null) ? pnl/(double)AMOUNT_MULTIPLIER : 0.0;
    }
}