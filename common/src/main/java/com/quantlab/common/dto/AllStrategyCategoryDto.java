package com.quantlab.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AllStrategyCategoryDto implements Serializable {
    private List<StrategyDto> diy;
    private List<StrategyDto> preBuilt;
    private List<StrategyDto> inHouse;
}
