package com.quantlab.signal.dto;

import com.quantlab.common.entity.Strategy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HoldingsDTO {

    Long exchangeInstrumentID;
    Long strategyID;
    //    String strategyLegID;
    String userID;
    Long signalID;
    Double signalPAndL;
    Double todaysPAndL;
    Double totalStrategyPAndL;
    Double OverAllUserPAndL;
    Double postionalPAndL;
    Double intradayPAndL;
    Double deployedCapital;
    PNLHeaderDTO liveHeaders;
    PNLHeaderDTO forwardHeader;
    ArrayList<StrategyLegTableDTO> strategyLegs;

    public HoldingsDTO(Long strategyId) {
        this.strategyID = strategyId;
        this.setExchangeInstrumentID(strategyID);
//        this.userID = strategy.getAppUser().getId().toString();
    }

}
