package com.quantlab.signal.dto;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyCategory;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalDto {
    private StrategyCategory strategyCategory;
    private UserRole userRole;
    private Strategy strategy;
    private StrategyLeg strategyLeg;
    private String executionType;
    private String status;
    private String deployedOn;
    private Long multiplier;
    private Long capital;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String deleteIndicator;

}
