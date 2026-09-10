package com.quantlab.signal.utils;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.common.utils.staticstore.dropdownutils.Segment;
import com.quantlab.common.utils.staticstore.dropdownutils.SegmentType;
import com.quantlab.signal.dto.HedgeData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.MasterRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.StrategyAppConfigData.INDEX_STRIKE_DIFFERENCE;
import static com.quantlab.signal.utils.staticdata.StaticStore.redisIndexStrikePrices;

@Component
@Slf4j
public class StrategyUtils {
    private record HedgeMasterCandidate(String key, MasterResponseFO master) {
    }

    private record HedgeCandidate(String key, MasterResponseFO master, MarketData marketData) {
    }

    @Autowired
    MasterRepository masterRepository;

    @Autowired
    @Qualifier("redisTemplate5")
    RedisTemplate<String, MasterResponseFO> redisTemplate;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    TouchLineService touchLineService;

    @PostConstruct
    public void init() {
        INDEX_STRIKE_DIFFERENCE.put("NIFTY", 100);
        INDEX_STRIKE_DIFFERENCE.put("BANKNIFTY", 200);
    }


    public StrategyLeg SignalStrategyLegMapper(Strategy strategy){
        StrategyLeg strategyLeg = new StrategyLeg();
        return strategyLeg;
    }

    public Signal StrategySignalMapper (Strategy strategy){
        Signal signal = new Signal();
        signal.setStrategy(strategy);
        signal.setCapital(strategy.getMinCapital());
        return signal;
    }

    public HedgeData getHedgeData(double ltp, int percent, String instrumentType, int ATM, String underlying, Strategy strategy, String condition) {
        double required = (ltp * percent) / 100;
        return getHedgeDataCommon(required, ATM, instrumentType, strategy, condition, "LTP");
    }

    public HedgeData getHedgeDataByDelta(double targetDelta, String instrumentType, int ATM, String underlying, Strategy strategy, String condition) {
        return getHedgeDataCommon(targetDelta, ATM, instrumentType, strategy, condition, "DELTA");
    }

    public HedgeData getHedgeDataCommon(double targetValue, int ATM, String instrumentType, Strategy strategy, String condition, String type) {
        return getHedgeDataCommon(targetValue, ATM, instrumentType, strategy, condition, type, null);
    }

    public HedgeData getHedgeDataCommon(double targetValue, int ATM, String instrumentType, Strategy strategy, String condition, String type, Long legId) {
        // Build Redis key pattern
        String underlyingName = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
        String expiryStr = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlyingName, OptionType.OPTION.getKey());
        String optionSuffix = instrumentType.equalsIgnoreCase(SegmentType.CE.getKey()) ? "CE" : "PE";
        String keyPattern = underlyingName + expiryStr + "*" + optionSuffix;
        log.info("Hedge data key pattern resolved | StrategyId={} | LegId={} | KeyPattern={} | ATM={} | Type={} | Condition={} | TargetValue={}",
                strategy.getId(), legId, keyPattern, ATM, type, condition, targetValue);

        double lowerLimit = ATM * 0.8;
        double upperLimit = ATM * 1.2;

        Set<String> masterKeys = getMasterKeysFromStrikeList(underlyingName, expiryStr, optionSuffix, lowerLimit, upperLimit);
        boolean usingGeneratedKeys = !masterKeys.isEmpty();
        if (masterKeys.isEmpty()) {
            masterKeys = scanMasterKeys(keyPattern, lowerLimit, upperLimit);
        }

        if (masterKeys.isEmpty()) {
            log.warn("No keys found in 20% range for pattern: {}", keyPattern);
            return null;
        }


        List<HedgeMasterCandidate> masterCandidates = getMasterCandidates(masterKeys);
        if (masterCandidates.isEmpty() && usingGeneratedKeys) {
            masterKeys = scanMasterKeys(keyPattern, lowerLimit, upperLimit);
            masterCandidates = getMasterCandidates(masterKeys);
        }

        List<String> instrumentIds = masterCandidates.stream()
                .map(candidate -> String.valueOf(candidate.master().getExchangeInstrumentID()))
                .toList();

        Map<String, MarketData> liveDataByInstrumentId = getLiveDataByInstrumentId(instrumentIds);

        List<HedgeCandidate> candidates = masterCandidates.stream()
                .map(candidate -> {
                    MasterResponseFO master = candidate.master();
                    MarketData marketData = liveDataByInstrumentId.get("MD_" + master.getExchangeInstrumentID());
                    if (marketData == null || marketData.getLTP() < 0.01) {
                        return null;
                    }
                    return new HedgeCandidate(candidate.key(), master, marketData);
                })
                .filter(Objects::nonNull)
                .toList();

        HedgeCandidate bestCandidate = switch (condition.toUpperCase()) {
            case "LESS_THAN" -> candidates.stream()
                    .filter(candidate -> getComparisonValue(candidate.marketData(), type) <= targetValue)
                    .min(Comparator.comparingDouble(candidate -> targetValue - getComparisonValue(candidate.marketData(), type)))
                    .orElse(null);
            case "GREATER_THAN" -> candidates.stream()
                    .filter(candidate -> getComparisonValue(candidate.marketData(), type) >= targetValue)
                    .min(Comparator.comparingDouble(candidate -> getComparisonValue(candidate.marketData(), type) - targetValue))
                    .orElse(null);
            case "NEAREST" -> candidates.stream()
                    .min(Comparator.comparingDouble(candidate -> Math.abs(getComparisonValue(candidate.marketData(), type) - targetValue)))
                    .orElse(null);
            default -> throw new IllegalArgumentException("Invalid condition: " + condition);
        };

        if (bestCandidate != null) {
            HedgeData hedgeData = new HedgeData();
            hedgeData.setLiveMarketData(bestCandidate.marketData());
            hedgeData.setMasterData(bestCandidate.master());
            hedgeData.setFinalKey(bestCandidate.key());
            return hedgeData;
        }

        log.warn("No suitable candidate found for target {}: {} and condition: {}", type, targetValue, condition);
        return null;
    }

    private double getComparisonValue(MarketData marketData, String type) {
        return type.equals("LTP") ? marketData.getLTP() : marketData.getDelta();
    }

    private Set<String> getMasterKeysFromStrikeList(String underlyingName,
                                                    String expiryStr,
                                                    String optionSuffix,
                                                    double lowerLimit,
                                                    double upperLimit) {
        List<Integer> strikeList = redisIndexStrikePrices.get(underlyingName + "_" + expiryStr);
        if (strikeList == null || strikeList.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> masterKeys = new HashSet<>();
        for (Integer strike : strikeList) {
            if (strike != null && strike >= lowerLimit && strike <= upperLimit) {
                masterKeys.add(underlyingName + expiryStr + "-" + strike + optionSuffix);
            }
        }
        return masterKeys;
    }

    private Set<String> scanMasterKeys(String keyPattern, double lowerLimit, double upperLimit) {
        Set<String> masterKeys = new HashSet<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(keyPattern).count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    String[] parts = key.split("-");
                    if (parts.length >= 2) {
                        String strikeWithSuffix = parts[parts.length - 1];
                        String strikeStr = strikeWithSuffix.replaceAll("[^0-9]", "");
                        if (!strikeStr.isEmpty()) {
                            int strike = Integer.parseInt(strikeStr);
                            if (strike >= lowerLimit && strike <= upperLimit) {
                                masterKeys.add(key);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Invalid key format: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Error while scanning Redis keys with pattern: {}", keyPattern, e);
        }
        return masterKeys;
    }

    private List<HedgeMasterCandidate> getMasterCandidates(Set<String> masterKeys) {
        return masterKeys.parallelStream()
                .map(key -> {
                    MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                    return master == null ? null : new HedgeMasterCandidate(key, master);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, MarketData> getLiveDataByInstrumentId(List<String> instrumentIds) {
        if (instrumentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MarketData> liveDataByInstrumentId = touchLineService.getMultipleTouchLines(instrumentIds);
        if (!liveDataByInstrumentId.isEmpty()) {
            return liveDataByInstrumentId;
        }

        Map<String, MarketData> fallback = new HashMap<>();
        for (String instrumentId : instrumentIds) {
            MarketData marketData = touchLineService.getTouchLine(instrumentId);
            if (marketData != null) {
                fallback.put("MD_" + instrumentId, marketData);
            }
        }
        return fallback;
    }

    public HedgeData getOptionStrikeByPremium(double targetPremium,
                                              int ATM,
                                              String instrumentType,
                                              Strategy strategy) {

        String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
        String expiry = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey());
        String suffix = instrumentType.equalsIgnoreCase(SegmentType.CE.getKey()) ? "CE" : "PE";

        String keyPattern = underlying + expiry + "*" + suffix;

        // --- Improved Strike Range ---
        int RANGE = 1500;
        int lower = ATM - RANGE;
        int upper = ATM + RANGE;

        Set<String> redisKeys = new HashSet<>();

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(keyPattern).build())) {

            while (cursor.hasNext()) {
                String key = cursor.next();

                String[] parts = key.split("-");
                if (parts.length < 2) continue;

                String strikeStr = parts[parts.length - 1].replaceAll("[^0-9]", "");
                if (strikeStr.isEmpty()) continue;

                int strike = Integer.parseInt(strikeStr);

                if (strike >= lower && strike <= upper) {
                    redisKeys.add(key);
                }
            }
        }

        if (redisKeys.isEmpty()) {
            log.warn("No strike keys found within ±1500 for {}", keyPattern);
            return null;
        }

        Map<Integer, String> keyMap = new ConcurrentHashMap<>();

        List<MasterResponseFO> masters = redisKeys.parallelStream()
                .map(k -> {
                    MasterResponseFO m = marketDataFetch.getMasterResponse(k);
                    if (m != null) keyMap.put(m.getExchangeInstrumentID(), k);
                    return m;
                })
                .filter(Objects::nonNull)
                .toList();

        List<MarketData> marketDataList = masters.parallelStream()
                .map(m -> touchLineService.getTouchLine(String.valueOf(m.getExchangeInstrumentID())))
                .filter(Objects::nonNull)
                .filter(md -> md.getLTP() > 0) // ignore zero LTP
                .toList();

        // --- NEW LOGIC: Choose strike whose LTP is closest to target ---
        MarketData best = marketDataList.stream()
                .min(Comparator.comparingDouble(md -> Math.abs(md.getLTP() - targetPremium)))
                .orElse(null);

        if (best == null) {
            log.warn("No suitable strike found near premium {}", targetPremium);
            return null;
        }

        HedgeData hd = new HedgeData();
        hd.setLiveMarketData(best);

        MasterResponseFO bestMaster = masters.stream()
                .filter(m -> m.getExchangeInstrumentID() == best.getExchangeInstrumentId())
                .findFirst()
                .orElse(null);

        hd.setMasterData(bestMaster);
        hd.setFinalKey(keyMap.get(best.getExchangeInstrumentId()));

        return hd;
    }



    public String getSegment(String underlyingName) {
        if (underlyingName.contains("NIFTY")) {
            return Segment.NSE.getKey();
        }else {
            return Segment.BSE.getKey();
        }
    }
}
