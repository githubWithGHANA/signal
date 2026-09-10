package com.quantlab.signal.dto.redisDto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MasterResponseFO  {
        private String exchangeSegment;
        private int exchangeInstrumentID;
        private String instrumentType;
        private int exchangeSegmentId;
        private String name;
        private String description;
        private String series;
        private String nameWithSeries;
        private long instrumentID;
        private double priceBandHigh;
        private double priceBandLow;
        private int freezeQty;
        private double tickSize;
        private int lotSize;
        private double multiplier;
        private String underlyingInstrumentId;
        private String underlyingIndexName;
        private int strikePrice;
        private String contractExpiration;
        private String optionType;
        private String priceNumerator;
        private String priceDenominator;
        private String displayName;
        private String instrumentKey;
        private String isin;

        // Constructor
//        public MasterResponseFO(String exchangeSegment, String exchangeInstrumentID,int exchangeSegmentId, String instrumentType, String name,
//                                String description, String series, String nameWithSeries, long instrumentID,
//                                double priceBandHigh, double priceBandLow, int freezeQty, double tickSize,
//                                int lotSize, double multiplier, String underlyingInstrumentId, String underlyingIndexName, String contractExpiration, String strikePrice,
//                                String optionType,String displayName, String priceNumerator, String priceDenominator) {
//            this.exchangeSegment = exchangeSegment;
//            this.exchangeInstrumentID = Integer.parseInt(exchangeInstrumentID);
//            this.exchangeSegmentId = exchangeSegmentId;
//            this.instrumentType = instrumentType;
//            this.name = name;
//            this.description = description;
//            this.series = series;
//            this.nameWithSeries = nameWithSeries;
//            this.instrumentID = instrumentID;
//            this.priceBandHigh = priceBandHigh;
//            this.priceBandLow = priceBandLow;
//            this.freezeQty = freezeQty;
//            this.tickSize = tickSize;
//            this.lotSize = lotSize;
//            this.multiplier = multiplier;
//            this.underlyingInstrumentId = underlyingInstrumentId;
//           this.underlyingIndexName = underlyingIndexName;
//            this.contractExpiration = contractExpiration;
//            this.strikePrice = strikePrice;
//            this.optionType = optionType;
//            this.displayName = displayName;
//            this.priceNumerator = priceNumerator;
//            this.priceDenominator = priceDenominator;
//        }



        // Getters and Setters
        // (You can generate these using your IDE)

        @Override
        public String toString() {
                return "ExchangeInstrument{" +
                        "exchangeSegment='" + exchangeSegment + '\'' +
                        ", exchangeInstrumentID='" + exchangeInstrumentID + '\'' +
                        ", instrumentType='" + instrumentType + '\'' +
                        ", name='" + name + '\'' +
                        ", description='" + description + '\'' +
                        ", series='" + series + '\'' +
                        ", nameWithSeries='" + nameWithSeries + '\'' +
                        ", instrumentID='" + instrumentID + '\'' +
                        ", priceBandHigh=" + priceBandHigh +
                        ", priceBandLow=" + priceBandLow +
                        ", freezeQty=" + freezeQty +
                        ", tickSize=" + tickSize +
                        ", lotSize=" + lotSize +
                        ", multiplier=" + multiplier +
                        ", underlyingInstrumentId='" + underlyingInstrumentId + '\'' +
                        ", contractExpiration='" + contractExpiration + '\'' +
                        ", strikePrice=" + strikePrice +
                        ", optionType='" + optionType + '\'' +
                        ", displayName='" + displayName + '\'' +
                        ", priceNumerator=" + priceNumerator +
                        ", priceDenominator=" + priceDenominator +
                        ", instrumentKey=" + instrumentKey +
                        '}';
        }


}
