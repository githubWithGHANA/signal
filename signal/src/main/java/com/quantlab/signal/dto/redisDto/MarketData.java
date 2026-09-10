package com.quantlab.signal.dto.redisDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketData {
    int exchangeSegment;
    int exchangeInstrumentId;
    long exchangeTimestamp;
    long lut;
    double LTP;
    double open;
    double high;
    double low;
    double close;
    double averageTradedPrice;
    int marketType;
    double delta;
    double IV;
}
