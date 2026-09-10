package com.quantlab.common.dao;

import lombok.Data;
import lombok.Getter;

import java.time.Instant;

@Getter
@Data
public class SignalStatsDto {
    private Long id;
    private Long profitLoss;
    private Instant createdAt;
    private Long multiplier;
    private Integer legCount;

    public SignalStatsDto(Long id, Long profitLoss, Instant createdAt, Long multiplier, Integer legCount) {
        this.id = id;
        this.profitLoss = profitLoss;
        this.createdAt = createdAt;
        this.multiplier = multiplier;
        this.legCount = legCount;
    }

}

