package com.quantlab.client.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.quantlab.common.utils.staticstore.IndexConstants.BANK_NIFTY_STRIKE_SIZE;
import static com.quantlab.common.utils.staticstore.IndexConstants.NIFTY_STRIKE_SIZE;

@Component
public class DiyStrategyUtils {
//
//    @Autowired
//    TouchLineService touchLineService;
//
//    @Autowired
//    MarketDataFetch marketDataFetch;
//
//
//    public Integer getDiyStrike (String strike, String underling,String optionType, Integer atm){
//        if (underling.equalsIgnoreCase("NIFTY")){
//            return getNiftyStrike(strike,underling,optionType,atm);
//        }else if (underling.equalsIgnoreCase("BANKNIFTY")){
//            return getBankNiftyStrike(strike,underling,optionType,atm);
//        }
//        return null;
//    }
//
//    public Integer getNiftyStrike(String strike, String underling,String optionType, Integer atm){
//        if (strike.equalsIgnoreCase("atm")) {
//            MarketLiveDto marketLiveDto = marketDataFetch.getMarketData(underling);
//            return marketLiveDto.getAtm();
//        } else if (strike.startsWith("otm")) {
//            Integer number = Integer.getInteger(strike.replaceAll("\\D+", ""));
//            return strikePriceFromString(strike, NIFTY_STRIKE_SIZE, number, atm, optionType);
//        } else if (strike.toLowerCase().startsWith("itm")) {
//            Integer number = Integer.getInteger(strike.replaceAll("\\D+", ""));
//            return strikePriceFromString(strike, NIFTY_STRIKE_SIZE, number, atm, optionType);
//        }
//        return  null;
//    }
//
//    public Integer getBankNiftyStrike(String strike, String underling,String optionType, Integer atm){
//        if (strike.equalsIgnoreCase("atm")) {
//            MarketLiveDto marketLiveDto = marketDataFetch.getMarketData(underling);
//            return marketLiveDto.getAtm();
//        } else if (strike.startsWith("otm")) {
//            Integer number = Integer.getInteger(strike.replaceAll("\\D+", ""));
//            return strikePriceFromString(strike, BANK_NIFTY_STRIKE_SIZE, number, atm, optionType);
//        } else if (strike.toLowerCase().startsWith("itm")) {
//            Integer number = Integer.getInteger(strike.replaceAll("\\D+", ""));
//            return strikePriceFromString(strike, BANK_NIFTY_STRIKE_SIZE, number, atm, optionType);
//        }
//        return  null;
//    }
//
//    public Integer strikePriceFromString (String strike,Integer count, Integer Increment, Integer atm, String optionType){
//        if (strike.toLowerCase().startsWith("otm")){
//            if (optionType.equalsIgnoreCase("call")){
//                return atm +(Increment*count);
//            }else if (optionType.equalsIgnoreCase("put")){
//                return atm -(Increment*count);
//            }
//        }else if (strike.toLowerCase().startsWith("itm")) {
//            if (optionType.equalsIgnoreCase("call")){
//                return atm -(Increment*count);
//            }else if (optionType.equalsIgnoreCase("put")){
//                return atm +(Increment*count);
//            }
//
//        }
//        return null;
//    }
}
