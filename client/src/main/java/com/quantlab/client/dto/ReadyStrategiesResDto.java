package com.quantlab.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReadyStrategiesResDto {

    private String category;
    private int totalStrategies;
    private List<StrategyDto> strategiesList;
}
