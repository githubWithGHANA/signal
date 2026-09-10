package com.quantlab.signal.utils;


import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.IndexDifference;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.SegmentType;
import com.quantlab.common.utils.staticstore.dropdownutils.StrikeSelectionMenu;
import com.quantlab.signal.dto.HedgeData;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import io.swagger.models.auth.In;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static com.quantlab.common.utils.staticstore.AppConstants.TOGGLE_TRUE;
import static com.quantlab.common.utils.staticstore.IndexConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.redisIndexStrikePrices;

@Component
public class DiyStrategyCommonUtil {

    private static final Logger logger = LoggerFactory.getLogger(DiyStrategyCommonUtil.class);

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    StrategyUtils strategyUtils;

    @Autowired
    CommonUtils commonUtils;


    @Autowired
    SyntheticPriceRepository syntheticPriceRepository;


//    public Integer getDiyStrike (String strike, String underling,String optionType){
//        if (underling.equalsIgnoreCase("NIFTY")){
//            MarketData nifty = touchLineService.getTouchLine("26000");
//            Integer strikeIndex = marketDataFetch.getATM("NIFTY",nifty.getLTP());
//            return getNiftyStrike(strike,underling,optionType,strikeIndex);
//        }else if (underling.equalsIgnoreCase("BANKNIFTY")){
//            MarketData banknifty = touchLineService.getTouchLine("26001");
//            Integer strikeIndex = marketDataFetch.getATM("NIFTY",banknifty.getLTP());
//            return getBankNiftyStrike(strike,underling,optionType,strikeIndex);
//        }
//        return null;
//    }

    public SignalMapperDto handlePremiumDeltaStrikeSelection(Strategy strategy, StrategyLeg leg) {
        // Determine the type (premium or delta) and condition based on sktSelection
        String sktSelection = leg.getSktSelection();
        String type;
        String condition;
        double targetValue = 0;

        if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_NEAREST.getKey())) {
            type = "LTP";
            condition = "NEAREST";
        } else if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_GREATERTHAN.getKey())) {
            type = "LTP";
            condition = "GREATER_THAN";
        } else if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_LESSTHAN.getKey())) {
            type = "LTP";
            condition = "LESS_THAN";
        } else if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_NEAREST.getKey())) {
            type = "DELTA";
            condition = "NEAREST";
        } else if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_GREATERTHAN.getKey())) {
            type = "DELTA";
            condition = "GREATER_THAN";
        } else if (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_LESSTHAN.getKey())) {
            type = "DELTA";
            condition = "LESS_THAN";
        } else {
            return null;
        }
        MarketLiveDto marketLiveDto = marketDataFetch.getMarketData(strategy.getUnderlying().getName(), strategy.getExpiry());

        try {
            targetValue = Double.parseDouble(leg.getSktSelectionValue());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in sktSelectionValue: " + leg.getSktSelectionValue());
        }

        HedgeData hedgeData = strategyUtils.getHedgeDataCommon(targetValue,
                marketLiveDto.getAtm(),
                leg.getOptionType(),
                strategy,
                condition,
                type,
                leg.getId());

        if (hedgeData == null || hedgeData.getMasterData() == null || hedgeData.getLiveMarketData() == null) {
            logger.error("No suitable option found for strike selection: {}", sktSelection);
            return null;
        }

        SignalMapperDto newDto = getSignalMapperDto(strategy, leg, hedgeData.getMasterData(), hedgeData.getLiveMarketData(), hedgeData.getFinalKey());

        return newDto;
    }

    public SignalMapperDto getSignalMapperDto(Strategy strategy, StrategyLeg leg, MasterResponseFO master, MarketData marketData, String key) {
        SignalMapperDto dto = new SignalMapperDto();
        dto.setLots(leg.getNoOfLots());
        dto.setBuySellFlag(leg.getBuySellFlag());
        dto.setLegType(leg.getLegType());
        dto.setPositionType(leg.getOptionType());
        dto.setOptionType(leg.getOptionType());
        dto.setSegment(leg.getSegment());
        dto.setDerivativeType(leg.getDerivativeType());
        dto.setName(key);
        dto.setTargetUnitType(leg.getTargetUnitType());
        dto.setTargetUnitValue(leg.getTargetUnitValue());
        dto.setTargetUnitToggle(leg.getTargetUnitToggle());
        dto.setStopLossUnitToggle(leg.getStopLossUnitToggle());
        dto.setStopLossUnitType(leg.getStopLossUnitType());
        dto.setStopLossUnitValue(leg.getStopLossUnitValue());
        if (TOGGLE_TRUE.equalsIgnoreCase(dto.getTrailingStopLossToggle())) {
            dto.setTrailingStopLossToggle(leg.getTrailingStopLossToggle());
            dto.setTrailingStopLossType(leg.getTrailingStopLossType());
            dto.setTrailingStopLossValue(leg.getTrailingStopLossValue());
            dto.setTrailingDistance(leg.getTrailingDistance());
        }

        if (master != null) {
            dto.setMasterData(master);
            dto.setLegName(key);
            dto.setQuantity((int) (leg.getNoOfLots() * master.getLotSize() * strategy.getMultiplier()));
        }

        if (marketData != null) {
            dto.setTouchlineBinaryResponse(marketData);
        }

        return dto;
    }

    public Integer getDiyStrike(Strategy strategy, StrategyLeg leg) {
        String optionType = leg.getOptionType();
        String underlying = strategy.getUnderlying().getName();
        String atmType = leg.getSktSelection();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);
        IndexDifference difference = IndexDifference.fromKey(underlying);

        List<Integer> strikeList = redisIndexStrikePrices.get(underlying+"_"+commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey()));
        if (strikeList == null || strikeList.isEmpty()) {
            logger.error("No strike prices found for {} on expiry {}", underlying, commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey()));
            return null;
        }
        strikeList.sort(Integer::compareTo);
        MarketData marketData = touchLineService.getTouchLine(String.valueOf(instrument.getLabel()));

        Integer atm;
        if (StrikeSelectionMenu.FUTURE_ATM.getKey().equalsIgnoreCase(atmType)) {
            String futureKey = underlying.toUpperCase(Locale.ROOT) +
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.FUTURE.getKey()) +
                    OptionType.FUTURE.getKey();
            MarketData futureData = touchLineService.getTouchLine(String.valueOf(marketDataFetch.getMasterResponse(futureKey).getExchangeInstrumentID()));
            atm = marketDataFetch.getATM(underlying, futureData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
            return atm;
        } else if (StrikeSelectionMenu.SYNTHETIC_ATM.getKey().equalsIgnoreCase(atmType)) {
            atm = marketDataFetch.getATM(underlying, syntheticPriceRepository.find(underlying).getPrice(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey()) );
            return atm;
        } else {
            atm = marketDataFetch.getATM(underlying, marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey()) );
            return getStrike(leg.getSktType(), underlying, optionType, atm,strikeList , strategy.getExpiry());
        }
    }


    public Integer getStrike(String strike, String underling, String optionType, Integer atm, List<Integer> strikeList, String expiry) {

        if (strike.toLowerCase().startsWith("atm")) {
            MarketLiveDto marketLiveDto = marketDataFetch.getMarketData(underling , commonUtils.getExpiryShotDateByIndex(expiry, underling, OptionType.OPTION.getKey()));
            return marketLiveDto.getAtm();
        } else if (strike.toLowerCase().startsWith("otm")) {
            String cleanString = strike.trim().replaceAll("\\D+", "");
            Integer number = cleanString.isEmpty() ? 1 : Integer.parseInt(cleanString);
            return getStrikeFromString(strike, optionType, atm,number, strikeList);
        } else if (strike.toLowerCase().startsWith("itm")) {
            String cleanString = strike.trim().replaceAll("\\D+", "");
            Integer number = cleanString.isEmpty() ? 1 : Integer.parseInt(cleanString);
            return getStrikeFromString(strike, optionType, atm,number, strikeList);
        } else {
            String cleanString = strike.trim().replaceAll("\\D+", "");
            Integer number = cleanString.isEmpty() ? 1 : Integer.parseInt(cleanString);
            return getStrikeFromString(strike, optionType, atm,number, strikeList);
        }
    }

    public Integer getStrikeFromString(String strike, String optionType, Integer atm, Integer number , List<Integer> strikeList) {

        Integer atmindex = !strikeList.contains(atm) ? 1 : strikeList.indexOf(atm);
        if (optionType.equalsIgnoreCase("ce")) {
            if (strike.toLowerCase().startsWith("otm")) {
                return strikeList.get(atmindex + number);
            } else if (strike.toLowerCase().startsWith("itm")) {
                return strikeList.get(atmindex - number);
            }
        } else if (optionType.equalsIgnoreCase("pe")) {
           if (strike.toLowerCase().startsWith("otm")) {
               return strikeList.get(atmindex - number);
            } else if (strike.toLowerCase().startsWith("itm")) {
               return strikeList.get(atmindex + number);
            }
        }
        return strikeList.get(atmindex);
    }


}
