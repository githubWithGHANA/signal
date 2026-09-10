package com.quantlab.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;

@Data
public class PositionTableResponseDto {

    private String sNo;
    private String sName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String dateTime;
    private String symbol;
    private double quantity;
    private Long price;
    private String status;
    private Long orderId;
    private String expiry;
    private String strike;
    private String mtm;
}
