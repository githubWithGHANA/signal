package com.quantlab.signal.positons;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegStatus;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.redisService.TouchLineService;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class Positions {

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    SignalRepository signalRepository;

    public Signal calculateSignalPNL(Signal signal){
        // has to calculate the position PNL
        Hibernate.initialize(signal.getStrategyLeg());
        List<StrategyLeg> legs = signal.getStrategyLeg();
        Long signalProfitLoss = 0L;
        for(StrategyLeg leg : legs){
            if (leg.getStatus().equalsIgnoreCase(LegStatus.OPEN.getKey())){
                MarketData marketPrice = touchLineService.getTouchLine(String.valueOf(leg.getExchangeInstrumentId()));
                if (marketPrice != null){

                    // has to calculate the profit and loss
                    double legPnL =  ( (leg.getQuantity() * leg.getExecutedPrice()) /(double) AMOUNT_MULTIPLIER) - (leg.getQuantity() *( marketPrice.getLTP()) );

                    marketPrice.getLTP();
                    // save on signal level and individual leg level
                    leg.setProfitLoss((long) (legPnL*AMOUNT_MULTIPLIER));

                    signalProfitLoss = (long) (signalProfitLoss + (legPnL*AMOUNT_MULTIPLIER));
                }
            }
        }
        signal.setProfitLoss(signalProfitLoss);
        signalRepository.save(signal);
        return signal;
    }




    public void calculatePnL(List<Strategy> strategies) {

        Long userProfitLoss = 0L;


        for (Strategy strategy : strategies) {


//       Signal updatedSignal = calculateSignalPNL(signal);
//        userProfitLoss = updatedSignal.getProfitLoss() + userProfitLoss;
//
//        }
            // has to save the user profit and loss
        }
    }


}
