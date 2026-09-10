package com.quantlab.common.dao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

@Getter
@ToString
@AllArgsConstructor
public class StrategySummaryDao {
    private final Long id;
    private final String name;
    private final String description;
    private final String atmType;
    private final Long multiplier;
    private final Long minCapital;
    private final Long underlyingId;
    private final String underlyingName;
    private final String positionType;
    private final String executionType;
    private final Instant createdAt;
    private final String strategyTag;
    private final String status;
    private final String category;
    private final Long drawDown;
    private final String subscription;
    private final String lastDeployedOn;
    private final Boolean isHidden;
    private final Integer reSignalCount;
    private final Long todayPNL;
    private final String expiry;
}
