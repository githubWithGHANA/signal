package com.quantlab.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReportsLegDto {

    private Long legId;

    private Long signalId;

    private Instant date;

    private String exchange;

    private Long Instrument;

    private String underlying;

    private Long quantity;

    private Double price;

    private Double sequentialPNL;
}