package com.quantlab.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class SignalSlimRow {
    private final Long signalId;
    private final Instant createdAt;
    private final Long profitLoss;    // raw long (paise)
    private final Long openLegCount;  // computed in SQL
    private final Long strategyId;
    private final String strategyName;
}
