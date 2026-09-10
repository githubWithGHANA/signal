package com.quantlab.signal.web.service;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.StrikeSelectionMenu;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.SyntheticPrice;
import com.quantlab.signal.service.redisService.MasterRepository;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.quantlab.signal.utils.staticdata.StaticStore.redisIndexStrikePrices;

@Component
public class MarketDataFetch {

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    private MasterRepository masterRepository;


    @Autowired
    SyntheticPriceRepository syntheticPriceRepository;


    private static final Logger logger = LoggerFactory.getLogger(MarketDataFetch.class);

    public MarketLiveDto getMarketData(String underling  , String expiry) {

        MarketLiveDto marketLiveDto = new MarketLiveDto();
        IndexInstruments instrument  = IndexInstruments.fromKey(underling);
        MarketData marketData = touchLineService.getTouchLine(String.valueOf(instrument.getLabel()));

        if (marketData == null) {
            logger.error("Market data unavailable for instrument: {}", underling);
            throw new RuntimeException("Market data not available for: " + underling);
        }
        validatePositiveLtp("underlying market data", underling, marketData.getLTP());

        marketLiveDto.setName(underling);
        marketLiveDto.setSpotPrice(marketData.getLTP());
        Integer atm = getATM(underling,marketData.getLTP() , commonUtils.getExpiryShotDateByIndex(expiry, underling, OptionType.FUTURE.getKey()));
        marketLiveDto.setAtm(atm);
        return  marketLiveDto;
    }

    public MarketLiveDto getMarketData(Strategy strategy) {
        String atmType = strategy.getAtmType();
        String underlying = strategy.getUnderlying().getName();

        MarketLiveDto marketLiveDto = new MarketLiveDto();

        IndexInstruments instrument = IndexInstruments.fromKey(underlying);

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(instrument.getLabel()));

        if (marketData == null) {
            logger.error("Market data unavailable for instrument: {} (strategyId={})", underlying, strategy.getId());
            throw new RuntimeException("Market data not available for: " + underlying);
        }

        marketLiveDto.setName(underlying);
        marketLiveDto.setSpotPrice(marketData.getLTP());

        Integer atm;
        if (StrikeSelectionMenu.FUTURE_ATM.getKey().equalsIgnoreCase(atmType)) {
            validatePositiveLtp("underlying market data", underlying, marketData.getLTP());
            String futureKey = underlying.toUpperCase(Locale.ROOT) +
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.FUTURE.getKey()) +
                    OptionType.FUTURE.getKey();
            MasterResponseFO masterResponse = getMasterResponse(futureKey);
            if (masterResponse == null) {
                logger.error("Master response unavailable for futureKey: {} (strategyId={})", futureKey, strategy.getId());
                throw new RuntimeException("Master response not available for: " + futureKey);
            }
            MarketData futureData = touchLineService.getTouchLine(String.valueOf(masterResponse.getExchangeInstrumentID()));
            if (futureData == null) {
                logger.error("Future market data unavailable for instrument: {} (strategyId={})", futureKey, strategy.getId());
                throw new RuntimeException("Future market data not available for: " + futureKey);
            }
            validatePositiveLtp("future market data", futureKey, futureData.getLTP());
            atm = getATM(underlying, futureData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        } else if (StrikeSelectionMenu.SYNTHETIC_ATM.getKey().equalsIgnoreCase(atmType)) {
            SyntheticPrice syntheticPrice = syntheticPriceRepository.find(underlying);
            if (syntheticPrice == null) {
                logger.error("Synthetic price unavailable for underlying: {} (strategyId={})", underlying, strategy.getId());
                throw new RuntimeException("Synthetic price not available for: " + underlying);
            }
            validatePositiveLtp("synthetic price", underlying, syntheticPrice.getPrice());
            atm = getATM(underlying,syntheticPrice.getPrice(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        } else {
            validatePositiveLtp("underlying market data", underlying, marketData.getLTP());
            atm = getATM(underlying, marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.FUTURE.getKey()) );
        }
        SyntheticPrice syntheticPrice = syntheticPriceRepository.find(underlying);
        if (syntheticPrice != null) {
            validatePositiveLtp("synthetic price", underlying, syntheticPrice.getPrice());
            marketLiveDto.setSyntheticAtm(getATM(underlying, syntheticPrice.getPrice(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey())));
            marketLiveDto.setSyntheticPrice(syntheticPrice.getPrice());
        }



        marketLiveDto.setAtm(atm);
        return marketLiveDto;
    }

    public Integer getATM(String underlying, double price , String expiry) {
        validatePositiveLtp("ATM price", underlying, price);
        List<Integer> strikeList = redisIndexStrikePrices.get(underlying+"_"+expiry);
        if (strikeList == null || strikeList.isEmpty()) {
            logger.error("Strike list unavailable for underlying: {} expiry: {}", underlying, expiry);
            throw new IllegalStateException("Strike list not available for: " + underlying + " expiry: " + expiry);
        }


        return findClosestStrike(strikeList, price);
    }


    public int findClosestStrike(List<Integer> strikeList, double ltp) {
        if (strikeList == null || strikeList.isEmpty()) {
            throw new IllegalArgumentException("Strike list must not be empty");
        }
        validatePositiveLtp("ATM search price", "unknown", ltp);
        int left = 0, right = strikeList.size() - 1;

        if (ltp <= strikeList.get(0)) return strikeList.get(0);
        if (ltp >= strikeList.get(right)) return strikeList.get(right);

        // Binary Search to find the closest strikes
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (strikeList.get(mid) == ltp) {
                return strikeList.get(mid);
            } else if (strikeList.get(mid) < ltp) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        // left is now the index of the closest higher value
        // right is the index of the closest lower value
        int lower = strikeList.get(right);
        int higher = strikeList.get(left);

        // Return the nearest value
        return (ltp - lower <= higher - ltp) ? lower : higher;
    }

    public MasterResponseFO getMasterResponse(String key) {
        try {
            MasterResponseFO responseFO = masterRepository.find(key);
            if (responseFO != null)
                return responseFO;
            throw new RuntimeException("no Master data found for key = "+key);
        } catch (Exception e) {
//            e.printStackTrace();
            logger.error("Error fetching master data for key: {} , message ={}", key, e.getMessage() );
//            logger.error(e.getMessage());
        }
        return null;
    }

    public MarketData getInstrumentData(Long instrumentId) {

        if (instrumentId != null) {
            MarketData marketData = touchLineService.getTouchLine(instrumentId.toString());
            if (marketData == null || !Double.isFinite(marketData.getLTP()) || marketData.getLTP() <= 0) {
                logger.warn("Ignoring invalid market data for instrumentId={}", instrumentId);
                return null;
            }
            return marketData;
        }
        return null;
    }

    public Map<String, List<String>> getInstrumentExpiryDates(ArrayList<String> expiryInstrumentIds) {
        Map<String, List<String>> redisData = new HashMap<>();
        for (String instrumentId : expiryInstrumentIds) {
                List<String> dates = touchLineService.getExpiryDates(instrumentId);
                redisData.put(instrumentId,dates);
        }
        return redisData;
    }

    public List<MasterResponseFO> getMasterResponseFO(String instrumentExpiryDateKey) {
        return touchLineService.getMasterResponseFO(instrumentExpiryDateKey);
    }

    public Double getSyntheticPrice (Strategy strategy,String underlying) {
        SyntheticPrice syntheticPrice = syntheticPriceRepository.find(underlying);
        if (syntheticPrice == null) {
            throw new IllegalStateException("Synthetic price not available for: " + underlying);
        }
        validatePositiveLtp("synthetic price", underlying, syntheticPrice.getPrice());
        return syntheticPrice.getPrice();
    }

    private void validatePositiveLtp(String source, String instrument, double price) {
        if (!Double.isFinite(price) || price <= 0) {
            logger.error("Invalid {} for instrument {}: {}", source, instrument, price);
            throw new IllegalStateException("Invalid " + source + " for " + instrument + ": " + price);
        }
    }
}
