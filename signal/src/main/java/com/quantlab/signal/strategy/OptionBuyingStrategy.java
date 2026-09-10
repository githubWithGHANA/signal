package com.quantlab.signal.strategy;


import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.TOGGLE_TRUE;

@Service("OptionBuyingStrategy")
public class OptionBuyingStrategy implements StrategiesImplementation<OptionBuyingStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(OptionBuyingStrategy.class);

    private static final LocalTime WATCHLIST_TIME = LocalTime.of(9, 20);
    private static final LocalTime EXIT_TIME = LocalTime.of(15, 15);
    private static final double BREAKOUT_MULTIPLIER = 1.30;   // 30% up
    private static final double STOPLOSS_PERCENT = 50.0;      // 50% SL on bought legs
    private static final int STRIKE_STEP_FALLBACK = 50;
    private static final int WATCHLIST_EXPIRE_HOUR = 16; // expire watchlist at 16:00 Asia/Kolkata

    @Autowired
    private MarketDataFetch marketDataFetch;
    @Autowired private SignalService signalService;
    @Autowired private StrategyRepository strategyRepository;
    @Autowired private CommonUtils commonUtils;
    @Autowired private GrpcService grpcService;
    @Autowired private CugUsersService cugUsersService;
    @Autowired private SignalRepository signalRepository;
    @Autowired private TouchLineService touchLineService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private StrategyService strategyService;

    private String watchlistRedisKey(Long strategyId) {
        return "watchlist:" + strategyId;
    }

    @Override
    public Signal runStrategy(Strategy strategy) {
        // runStrategy will be used for inHouseEntry trigger — we treat it as "create watchlist now"
        try {
            addAtmToWatchlist(strategy, strategy.getId());
            attemptEntryFromWatchlist(strategy);
            return null;
        } catch (Exception e) {
            logger.error("OptionBuying runStrategy error for strategyId={} msg={}", strategy.getId(), e.getMessage(), e);
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            logger.info("Exiting OptionBuying strategy: {}", strategy.getId());
            Signal exitSignal = signalService.createExit(strategy);
            if (exitSignal != null
                    && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && cugUsersService.isUserInCug(exitSignal.getAppUser().getTenentId())) {
                grpcService.sendExitSignal(exitSignal);
                logger.info("Exit signal sent for strategy: {}", strategy.getId());
            } else {
                logger.info("Exit skipped for strategy: {} (paper trading or no exit signal)", strategy.getId());
            }
            // cleanup watchlist if exists
            try { redisTemplate.delete(watchlistRedisKey(strategy.getId())); } catch (Exception ignored) {}
        } catch (Exception e) {
            logger.error("Error while exiting OptionBuying strategy id={}", strategy.getId(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        if (strategyService.inHouseEntryCheck(strategy)) {
            logger.info("InHouse Entry Check Passed for OptionSelling strategy: {}", strategy.getId());
            this.runStrategy(strategy);
        }
        else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Long topSignalId = signalRepository.findTopIdByStrategyIdAndStatusOrderByCreatedAtDesc(strategy.getId(), SignalStatus.LIVE.getKey());
            if (topSignalId != null) {
                boolean check = strategyService.optionBuyingExit(strategy, topSignalId);
                if (check) {
                    this.exitStrategy(strategy);
                }
            }
        }
    }

    // ------------------- Watchlist helpers -------------------

    private boolean isWatchlistPresent(Long strategyId) {
        try {
            String key = watchlistRedisKey(strategyId);
            Boolean has = redisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(has)) return false;
            HashOperations<String, String, Double> ops = redisTemplate.opsForHash();
            return ops.size(key) > 0;
        } catch (Exception e) {
            logger.warn("isWatchlistPresent error for strategyId={} err={}", strategyId, e.getMessage());
            return false;
        }
    }

    private void addAtmToWatchlist(Strategy strategy, Long strategyId) {
        try {
            if (isWatchlistPresent(strategyId)) {
                logger.debug("Watchlist already exists for strategyId={}, skipping creation", strategyId);
                return;
            }
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            String expiryShot = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey());
            double spot = marketLive.getSpotPrice();

            int atm = marketDataFetch.getATM(underlying, spot, expiryShot);

            String ceKey = underlying + expiryShot + "-" + atm + "CE";
            String peKey = underlying + expiryShot + "-" + atm + "PE";

            MasterResponseFO ceMaster = marketDataFetch.getMasterResponse(ceKey);
            MasterResponseFO peMaster = marketDataFetch.getMasterResponse(peKey);

            MarketData ceMd = ceMaster != null ? touchLineService.getTouchLine(String.valueOf(ceMaster.getExchangeInstrumentID())) : null;
            MarketData peMd = peMaster != null ? touchLineService.getTouchLine(String.valueOf(peMaster.getExchangeInstrumentID())) : null;

            String redisKey = watchlistRedisKey(strategyId);
            HashOperations<String, String, Double> ops = redisTemplate.opsForHash();

            boolean added = false;
            if (ceMd != null && ceMd.getLTP() > 0) {
                ops.put(redisKey, ceKey, ceMd.getLTP());
                logger.info("Added to watchlist: {} baselineLTP={} strategyId={}", ceKey, ceMd.getLTP(), strategyId);
                added = true;
            } else {
                logger.warn("Could not add CE {} to watchlist (no LTP) for strategyId={}", ceKey, strategyId);
            }

            if (peMd != null && peMd.getLTP() > 0) {
                ops.put(redisKey, peKey, peMd.getLTP());
                logger.info("Added to watchlist: {} baselineLTP={} strategyId={}", peKey, peMd.getLTP(), strategyId);
                added = true;
            } else {
                logger.warn("Could not add PE {} to watchlist (no LTP) for strategyId={}", peKey, strategyId);
            }

            if (added) {
                // expire at today 16:00 Asia/Kolkata
                Instant expireAt = LocalDateTime.of(LocalDate.now(ZoneId.of("Asia/Kolkata")), LocalTime.of(WATCHLIST_EXPIRE_HOUR, 0))
                        .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
                redisTemplate.expireAt(redisKey, Date.from(expireAt));
            }
        } catch (Exception e) {
            logger.error("Error adding ATM legs to watchlist for strategyId={} msg={}", strategy.getId(), e.getMessage(), e);
        }
    }

    private void attemptEntryFromWatchlist(Strategy strategy) {
        Long strategyId = strategy.getId();
        String redisKey = watchlistRedisKey(strategyId);
        HashOperations<String, String, Double> ops = redisTemplate.opsForHash();

        try {
            Map<String, Double> watchEntries = ops.entries(redisKey);
            if (watchEntries.isEmpty()) return;

            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            List<String> orderedKeys = new ArrayList<>(watchEntries.keySet());
            Collections.sort(orderedKeys); // deterministic

            for (String legKey : orderedKeys) {
                Double baseline = watchEntries.get(legKey);
                if (baseline == null || baseline <= 0) continue;

                MasterResponseFO master = marketDataFetch.getMasterResponse(legKey);
                if (master == null) continue;
                MarketData md = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                if (md == null || md.getLTP() <= 0) continue;

                double currentLtp = md.getLTP();
                if (currentLtp >= baseline * BREAKOUT_MULTIPLIER) {
                    // breakout -> buy this single leg and stop monitoring
                    SignalMapperDto dto = buildMapperForBuy(strategy, marketLive, legKey, master, 1, STOPLOSS_PERCENT);
                    List<SignalMapperDto> toBuy = Collections.singletonList(dto);

                    Signal signal = signalService.createSignal(strategy, toBuy);
                    strategyRepository.updateSignalCount(strategyId);

                    // remove watchlist completely (we only buy one leg)
                    try { redisTemplate.delete(redisKey); } catch (Exception ignored) {}

                    if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                            && cugUsersService.isUserInCug(signal.getAppUser().getTenentId())) {
                        grpcService.sendSignal(signal);
                    }

                    logger.info("OptionBuying: breakout buy created for strategyId={} leg={} signalId={}", strategyId, legKey, signal.getId());
                    return;
                }
            }
        } catch (Exception ex) {
            logger.error("Error attemptEntryFromWatchlist for strategyId={} err={}", strategyId, ex.getMessage(), ex);
        }
    }

    private SignalMapperDto buildMapperForBuy(Strategy strategy,
                                              MarketLiveDto marketLive,
                                              String legName,
                                              MasterResponseFO master,
                                              long lots,
                                              double stoplossPercent) {
        SignalMapperDto dto = new SignalMapperDto();
        dto.setMarketLiveDto(marketLive);
        MarketData touchLine = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
        dto.setTouchlineBinaryResponse(touchLine);
        dto.setMasterData(master);
        dto.setLegName(legName);
        dto.setBuySellFlag(LegSide.BUY.getKey());
        dto.setSegment("NSEFO");
        boolean isCE = legName.endsWith("CE");
        dto.setCategory(isCE ? LegType.CALL.getKey() : LegType.PUT.getKey());
        dto.setOptionType(isCE ? SegmentType.CE.getKey() : SegmentType.PE.getKey());
        dto.setPositionType(strategy.getPositionType());
        dto.setLots(lots);
        dto.setLegType(LegType.OPEN.getKey());
        dto.setDerivativeType(OptionType.OPTION.getKey());
        dto.setQuantity((int) (lots * master.getLotSize() * strategy.getMultiplier()));

        // Set 50% stoploss as percent on premium
        dto.setStopLossUnitToggle(TOGGLE_TRUE);
        dto.setStopLossUnitType("PERCENT");
        dto.setStopLossUnitValue((long) Math.round(stoplossPercent));


        return dto;
    }

}

