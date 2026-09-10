package com.quantlab.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportsSignalDto {

    private Long signalId;

    private String Date;

    private Double pnl;

    private Double pnlChange;

    private Double sequentialPNL;

    private Long orderCount;

    private List<ReportsLegDto> reportsLegDtoList;
}
