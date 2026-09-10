package com.quantlab.client.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class AllReportsRequestDto {

    private Instant fromDate;

    private Instant toDate;

    private Long strategyId;

    private Long brokerId;
}