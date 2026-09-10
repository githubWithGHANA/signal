package com.quantlab.signal.dto.redisDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CandleData {
    private long epochTime;   // use raw timestamp as returned by API
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}