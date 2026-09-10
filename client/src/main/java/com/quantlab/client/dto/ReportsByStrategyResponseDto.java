package com.quantlab.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportsByStrategyResponseDto {

    private String strategyName;

    private Long strategyID;

    List<ReportsSignalDto> reportsSignalDTOs;

    private Long totalOrders;

    Double totalPNL;

}