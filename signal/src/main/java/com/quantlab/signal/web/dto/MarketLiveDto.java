package com.quantlab.signal.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketLiveDto {
    private Double spotPrice;
    private Double futurePrice;
    private String name;
    private Number exchangeInstrumentID;
    private Number strike;
    private Number exchangeName;
    private Number exchangeSymbol;
    private Number exchangeType;
    private Number cePrice;
    private Number pePrice;
    private Integer atm;
    private Double syntheticPrice;
    private Integer syntheticAtm;
}
