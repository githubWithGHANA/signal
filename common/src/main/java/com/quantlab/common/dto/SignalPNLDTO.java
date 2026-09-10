package com.quantlab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignalPNLDTO {
    private Long id;
    private Long strategyId;
    private Long profitLoss;
    private Long userId;
    private Long baseIndexPrice;
    private Long latestIndexPrice;
    private Long signalAdditionID;
    private String positionType;
    private String executionType;
}
