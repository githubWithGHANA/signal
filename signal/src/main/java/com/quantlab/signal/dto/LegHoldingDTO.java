package com.quantlab.signal.dto;

import com.quantlab.common.dto.SignalPNLDTO;
import com.quantlab.common.dto.StrategyLegDto;
import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.dropdownutils.LegSide;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Data
@NoArgsConstructor
public class LegHoldingDTO {

    private long legId;
    private Double legLTP;
    private String buyOrSellFlag;
    private Long instrumentId;
    private long legQuantity;
    private String name;
    private Long lots;
    private Double pAndL;
    private Long signalId;
    private Double executedPrice; //its called traded price in the ui;
    private Double currentIV;
    private Double currentDelta;
    private Double constantIV;
    private Double constantDelta;
    private String deployedTimeStamp;
    private String exitTime;
    private Double exitPrice;
    private Double indexBasePrice;
    private Double indexCurrentPrice;


    public LegHoldingDTO(StrategyLegPNLDTO strategyLeg, double ltp, SignalPNLDTO signal){
        this.legId = strategyLeg.getId();
        this.buyOrSellFlag = strategyLeg.getBuySellFlag() ;
        this.instrumentId = strategyLeg.getExchangeInstrumentId() ;
        this.legQuantity = strategyLeg.getFilledQuantity()/strategyLeg.getLotSize();
        if (buyOrSellFlag.equalsIgnoreCase(LegSide.SELL.getKey()))
            this.legQuantity = -1*legQuantity;
        this.name = strategyLeg.getName() ;
        this.lots = strategyLeg.getNoOfLots() ;
        this.signalId = strategyLeg.getSignalId();
        this.pAndL =  roundToTwoDecimalPlaces((strategyLeg.getProfitLoss()/(double) AMOUNT_MULTIPLIER));
        this.legLTP = roundToTwoDecimalPlaces(ltp);
        this.executedPrice = roundToTwoDecimalPlaces(strategyLeg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER);
        this.currentIV = roundToTwoDecimalPlaces(strategyLeg.getCurrentIV() / (double) AMOUNT_MULTIPLIER);
        this.currentDelta = roundToTwoDecimalPlaces(strategyLeg.getCurrentDelta()/ (double) GREEK_MULTIPLIER);
        this.constantIV = roundToTwoDecimalPlaces(strategyLeg.getConstantIV() != null? (strategyLeg.getConstantDelta() / (double) AMOUNT_MULTIPLIER):0);
        this.constantDelta = roundToTwoDecimalPlaces(strategyLeg.getConstantDelta() != null? (strategyLeg.getConstantDelta()/ (double) GREEK_MULTIPLIER):0);
        if (signal.getLatestIndexPrice() != null)
            this.indexCurrentPrice = signal.getLatestIndexPrice()/(double)AMOUNT_MULTIPLIER;
        if (signal.getBaseIndexPrice() != null)
            this.indexBasePrice = signal.getBaseIndexPrice()/(double)AMOUNT_MULTIPLIER;
    }


}
