package com.quantlab.common.dao;

import lombok.Data;

@Data
public class StrategyStatusResignalDao {
    private String status;
    private Integer reSignalCount;
    private Integer signalCount;
    private String manualExitType;

    public StrategyStatusResignalDao(String status, Integer reSignalCount, Integer signalCount, String manualExitType) {
        this.status = status;
        this.reSignalCount = reSignalCount;
        this.signalCount = signalCount;
        this.manualExitType = manualExitType;
    }
}

