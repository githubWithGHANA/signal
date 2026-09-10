package com.quantlab.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class AllReportsResponseDto {

    private List<AllReportsSingleResponseDto> reports;

    private Double totalPnl;

}