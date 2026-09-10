package com.quantlab.common.dto;

import com.quantlab.common.entity.StrategyLeg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StrategyLegPNLDTO {

        private Long id;
        private Long ltp;
        private Long profitLoss;
        private Long currentIV;
        private Long currentDelta;
        private String buySellFlag;
        private Long filledQuantity;
        private Long price;
        private String name;
        private String status;
        private String legType;
        private Long lotSize;
        private Long noOfLots;
        private Long signalId;
        private Long exchangeInstrumentId;
        private Long executedPrice;
        private Long constantIV;
        private Long constantDelta;
        private Long latestIndexPrice;
        private Long baseIndexPrice;


    public StrategyLegPNLDTO(Long id, Long ltp, Long profitLoss, Long currentIV, Long currentDelta,
                             String buySellFlag, Long filledQuantity, Long price, String name,
                             String status, String legType, Long lotSize, Long noOfLots,
                             Long signalId, Long exchangeInstrumentId, Long executedPrice,
                             Long constantIV, Long constantDelta, Long latestIndexPrice,
                             Long baseIndexPrice) {
        this.id = id;
        this.ltp = ltp;
        this.profitLoss = profitLoss;
        this.currentIV = currentIV;
        this.currentDelta = currentDelta;
        this.buySellFlag = buySellFlag;
        this.filledQuantity = filledQuantity;
        this.price = price;
        this.name = name;
        this.status = status;
        this.legType = legType;
        this.lotSize = lotSize;
        this.noOfLots = noOfLots;
        this.signalId = signalId;
        this.exchangeInstrumentId = exchangeInstrumentId;
        this.executedPrice = executedPrice;
        this.constantIV = constantIV;
        this.constantDelta = constantDelta;
        this.latestIndexPrice = latestIndexPrice;
        this.baseIndexPrice = baseIndexPrice;
    }

}
