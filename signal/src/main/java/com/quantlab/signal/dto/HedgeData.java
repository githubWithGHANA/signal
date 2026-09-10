package com.quantlab.signal.dto;

import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import lombok.Data;

@Data
public class HedgeData {
    private MasterResponseFO masterData;
    private MarketData liveMarketData;
    private String finalKey;
}
