package com.quantlab.common.dao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class ActiveStrategyDao {
    private final String name;
    private final Long sId;
    private final String deployedOn;
    private final String execution;
    private final String status;
    private final Long capital;
    private final Long multiplier;
    private final Integer counter;
    private final String category;
    private final Long requiredCapital;
    private final String positionType;
    private final Boolean isHidden;
    private final Integer reSignalCount;
    private final Long todayPNL;
}
