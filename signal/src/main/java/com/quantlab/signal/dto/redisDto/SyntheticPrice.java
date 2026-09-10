package com.quantlab.signal.dto.redisDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyntheticPrice {
    private String symbol;
    private String name;
    private double price;
    private long timestamp;
    private int exchangeSegment;
    private int exchangeInstrumentId;
    private Long exchangeTimestamp;
    private Long lut;
}
