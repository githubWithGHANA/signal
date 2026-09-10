package com.quantlab.client.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.quantlab.common.config.LocalDateToInstantDeserializer;
import lombok.Data;

import java.time.Instant;

@Data
public class ReportsByStrategyRequestDto {

    @JsonDeserialize(using = LocalDateToInstantDeserializer.class)
    private Instant fromDate;

    @JsonDeserialize(using = LocalDateToInstantDeserializer.class)
    private Instant toDate;

    private Long strategyId;

    private Long brokerId;

}
