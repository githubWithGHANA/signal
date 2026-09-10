package com.quantlab.client.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class OrderTableResponseDto {

    private String sNo;
    private String sName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String dateTime;
    private String symbol;
    private Long quantity;
    private Double price;
    private String status;
    private Long orderId;
}
