package com.quantlab.signal.strategy;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.CandleData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.CandleRedisService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service("BhuvasTrendFollowingStrategy")
public class BhuvasTrendFollowingStrategy implements StrategiesImplementation<BhuvasTrendFollowingStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(BhuvasTrendFollowingStrategy.class);

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 15);
    private static final LocalTime FIRST_CANDLE_CLOSE_TIME = LocalTime.of(9, 30);
    private static final LocalTime HARD_EXIT_TIME = LocalTime.of(15, 15);
    private static final long IST_EPOCH_OFFSET_SECONDS = Duration.ofHours(5).plusMinutes(30).getSeconds();

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private CandleRedisService candleRedisService;

    @Autowired
    private StrategyAdditionsRepository strategyAdditionsRepository;

    @Autowired
    private DeploymentErrorsRepository deploymentErrorsRepository;

    private static final String PHASE_ENTRY = "ENTRY";
    private static final String PHASE_WATCHING = "WATCHING";
    private static final String PHASE_TRENDING = "TRENDING";
    private static final String PHASE_RETRACEMENT = "RETRACEMENT";
    private static final String PHASE_GAP = "GAP";
    private static final String DIR_LONG = "LONG";
    private static final String DIR_SHORT = "SHORT";

    @Override
    public Signal runStrategy(Strategy strategy) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            Hibernate.initialize(strategy.getStrategyAdditions());
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);

            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            String derivativeType = OptionType.FUTURE.getKey();

            String symbol = underlying + commonUtils.getExpiryShotDateByIndex(
                    strategy.getExpiry(),
                    strategy.getUnderlying().getName(),
                    OptionType.FUTURE.getKey()
            ) + OptionType.FUTURE.getKey();

            MasterResponseFO master = marketDataFetch.getMasterResponse(symbol);
            if (master == null) {
                logger.error("No master found for symbol {}", symbol);
                return null;
            }
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));

            SignalMapperDto leg = new SignalMapperDto();
            leg.setMarketLiveDto(marketLive);
            leg.setTouchlineBinaryResponse(touchline);
            leg.setMasterData(master);
            leg.setLegName(symbol);
            leg.setBuySellFlag(strategy.getRemarks() != null && strategy.getRemarks().equals("SELL") ? LegSide.SELL.getKey() : LegSide.BUY.getKey());
            leg.setSegment("NSEFO");
            leg.setCategory(derivativeType);
            leg.setOptionType(derivativeType);
            leg.setDerivativeType(derivativeType);
            leg.setPositionType(strategy.getPositionType());
            leg.setLots(1L);
            leg.setLegType("OPEN");
            leg.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

            String signalType = strategy.getRemarks() != null ? strategy.getRemarks() : "BUY";
            strategy.setRemarks(null);

            Signal createdSignal = signalService.createSignal(strategy, Collections.singletonList(leg));
            strategyRepository.updateSignalCount(strategy.getId());

            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
                grpcService.sendSignal(createdSignal);
            }

            logBhuvasSignalData(strategy, createdSignal, signalType);

            return createdSignal;

        } catch (Exception e) {
            logger.error("Error running BhuvasTrendFollowingStrategy for strategyId={}", strategy.getId(), e);
            signalService.errorCreatingSignal(strategy, e);
            return null;
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        this.exitStrategy(strategy, null);
    }

    @Transactional
    public void exitStrategy(Strategy strategy, Double exitPrice) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            Signal exit = signalService.createExit(strategy, exitPrice);
            if (exit != null && !ExecutionTypeMenu.PAPER_TRADING.getKey().equalsIgnoreCase(strategy.getExecutionType())) {
                grpcService.sendExitSignal(exit);
            }
            logger.info("BhuvasTrendFollowingStrategy exited: {} at price {}", strategy.getId(), exitPrice);
            logBhuvasExitData(strategy, "Strategy Exit Triggered", exitPrice);
        } catch (Exception ex) {
            logger.error("Error exiting BhuvasTrendFollowingStrategy: {}", strategy.getId(), ex);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        try {
            Strategy fresh = strategyRepository.findByIdWithAdditions(strategy.getId())
                    .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Strategy not found"));

            StrategyAdditions additions = fresh.getStrategyAdditions();
            String phase = additions != null ? additions.getBrrPhase() : "NULL";

            logger.info("[BRR] Checking strategyId={}, status={}, phase={}", fresh.getId(), fresh.getStatus(), phase);

            if (!fresh.getStatus().equalsIgnoreCase(Status.LIVE.getKey())
                    && shouldResetInactiveBrrState(additions)) {
                logger.info("[BRR] Strategy {} - Resetting inactive BRR runtime state before entry. LastCandleTime={}",
                        fresh.getId(), additions.getBrrLastCandleTime());
                resetAdditions(additions);
                phase = "NULL";
            }

            if (!fresh.getStatus().equalsIgnoreCase(Status.LIVE.getKey())
                    && (PHASE_TRENDING.equals(phase) || PHASE_RETRACEMENT.equals(phase))) {
                logger.warn("[BRR] Strategy {} - Orphaned phase '{}' with status={}. Resetting.",
                        fresh.getId(), phase, fresh.getStatus());
                resetAdditions(additions);
                return;
            }

            if (fresh.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                if (checkExit(fresh)) {
                    logger.info("[BRR] Strategy {} - Exit triggered.", fresh.getId());
                    this.exitStrategy(fresh);
                    resetAdditions(additions);
                    return;
                }

                if ((PHASE_TRENDING.equals(phase) || PHASE_RETRACEMENT.equals(phase))
                        && !hasActiveBrrPositionState(additions)) {
                    logger.warn("[BRR] Strategy {} - Invalid active BRR state for phase={}. Skipping phase processing.",
                            fresh.getId(), phase);
                    return;
                }

                if (PHASE_RETRACEMENT.equals(phase)) {
                    processPhase3(fresh);
                } else {
                    processPhase2(fresh);
                }
            } else if (strategyService.inHouseEntryCheck(fresh)) {
                processPhase1(fresh);
            } else {
                logger.debug("[BRR] Strategy {} - No action required in current state.", fresh.getId());
            }
        } catch (Exception e) {
            logger.error("Error in BhuvasTrendFollowingStrategy check() for strategyId={}", strategy.getId(), e);
        }
    }

    private boolean shouldResetInactiveBrrState(StrategyAdditions additions) {
        if (additions == null || !hasBrrRuntimeState(additions)) {
            return false;
        }

        if (LocalTime.now(ZONE_ID).isBefore(FIRST_CANDLE_CLOSE_TIME)) {
            return true;
        }

        Long lastCandleTime = additions.getBrrLastCandleTime();
        if (lastCandleTime == null) {
            return true;
        }

        LocalDate candleDate = toFeedCandleZonedDateTime(lastCandleTime).toLocalDate();
        return !LocalDate.now(ZONE_ID).equals(candleDate);
    }

    private boolean hasBrrRuntimeState(StrategyAdditions additions) {
        return additions.getBrrPhase() != null
                || additions.getBrrHigh() != null
                || additions.getBrrLow() != null
                || additions.getBrrResistance() != null
                || additions.getBrrSupport() != null
                || additions.getBrrStopLoss() != null
                || additions.getBrrDirection() != null
                || additions.getBrrLastCandleTime() != null;
    }

    private boolean hasBrrReferenceState(StrategyAdditions additions) {
        return additions.getBrrHigh() != null && additions.getBrrLow() != null;
    }

    private boolean hasBrrWatchingState(StrategyAdditions additions) {
        return additions.getBrrResistance() != null && additions.getBrrSupport() != null;
    }

    private boolean hasActiveBrrPositionState(StrategyAdditions additions) {
        if (additions == null) {
            return false;
        }

        String direction = additions.getBrrDirection();
        boolean validDirection = DIR_LONG.equals(direction) || DIR_SHORT.equals(direction);

        return validDirection
                && additions.getBrrStopLoss() != null
                && additions.getBrrHigh() != null
                && additions.getBrrLow() != null;
    }

    private void resetAdditions(StrategyAdditions additions) {
        if (additions != null) {
            additions.setBrrPhase(null);
            additions.setBrrHigh(null);
            additions.setBrrLow(null);
            additions.setBrrResistance(null);
            additions.setBrrSupport(null);
            additions.setBrrStopLoss(null);
            additions.setBrrDirection(null);
            additions.setBrrLastCandleTime(null);
            strategyAdditionsRepository.save(additions);
            logger.info("[BRR] StrategyAdditions reset for strategyId={}",
                    additions.getStrategy() != null ? additions.getStrategy().getId() : "unknown");
        }
    }

    private boolean checkExit(Strategy strategy) {
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                logger.info("[BRR] Manual exit triggered | StrategyId={}", strategy.getId());
                return true;
            }

            LocalTime nowIST = LocalTime.now(ZONE_ID);
            if (!nowIST.isBefore(HARD_EXIT_TIME)) {
                logger.info("[BRR] Hard exit time reached | Now={} | StrategyId={}", nowIST, strategy.getId());
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("[BRR] Error in checkExit | StrategyId={}", strategy.getId(), e);
            return false;
        }
    }

    private void processPhase1(Strategy strategy) {
        LocalTime nowIST = LocalTime.now(ZONE_ID);
        if (nowIST.isBefore(FIRST_CANDLE_CLOSE_TIME)) {
            logger.debug("[BRR] Strategy {} - Waiting for first 15-min candle close. Now={}, FirstClose={}",
                    strategy.getId(), nowIST, FIRST_CANDLE_CLOSE_TIME);
            return;
        }

        StrategyAdditions additions = strategy.getStrategyAdditions();
        if (additions == null) {
            additions = new StrategyAdditions();
            additions.setStrategy(strategy);
            strategy.setStrategyAdditions(additions);
        }

        List<CandleData> candles = getFuturesCandles(strategy, 30);
        if (candles == null || candles.isEmpty()) {
            logger.warn("[BRR] Strategy {} - Insufficient candles for processPhase1: {}",
                    strategy.getId(), (candles == null ? "null" : candles.size()));
            return;
        }

        candles.sort(Comparator.comparingLong(CandleData::getEpochTime));

        CandleData currCandle = candles.get(candles.size() - 1);
        long currTime = currCandle.getEpochTime();

        if (additions.getBrrLastCandleTime() != null && additions.getBrrLastCandleTime() == currTime) {
            // Already processed this candle
            return;
        }

        String phase = additions.getBrrPhase();

        if (phase == null || phase.isEmpty()) {
            Optional<CandleData> firstMarketCandle = findFirstMarketCandle(candles);
            if (firstMarketCandle.isEmpty()) {
                logger.warn("[BRR] Strategy {} - First 15-min candle not available yet after {}",
                        strategy.getId(), FIRST_CANDLE_CLOSE_TIME);
                return;
            }

            CandleData referenceCandle = firstMarketCandle.get();
            double refHigh = Math.max(referenceCandle.getOpen(), referenceCandle.getClose());
            double refLow = Math.min(referenceCandle.getOpen(), referenceCandle.getClose());

            additions.setBrrHigh(refHigh);
            additions.setBrrLow(refLow);
            additions.setBrrPhase(PHASE_ENTRY);
            additions.setBrrLastCandleTime(currTime);
            logger.info("[BRR] Strategy {} - Initialized ENTRY from first 15-min candle (9:15-9:30). Ref High: {}, Ref Low: {}",
                    strategy.getId(), refHigh, refLow);
            strategyAdditionsRepository.save(additions);
            return;
        }

        CandleData prevCandle = candles.size() > 1 ? candles.get(candles.size() - 2) : currCandle;

        double c1High = Math.max(prevCandle.getOpen(), prevCandle.getClose());
        double c1Low = Math.min(prevCandle.getOpen(), prevCandle.getClose());
        double c2High = Math.max(currCandle.getOpen(), currCandle.getClose());
        double c2Low = Math.min(currCandle.getOpen(), currCandle.getClose());

        logger.info("[BRR] Strategy {} Phase 1 - Phase: {}, C1 High/Low: {}/{}, C2 High/Low: {}/{}",
                strategy.getId(), phase, c1High, c1Low, c2High, c2Low);

        if ((phase.equals(PHASE_ENTRY) || phase.equals(PHASE_GAP)) && !hasBrrReferenceState(additions)) {
            logger.warn("[BRR] Strategy {} - Invalid Phase 1 reference state for phase={}. Resetting.",
                    strategy.getId(), phase);
            resetAdditions(additions);
            return;
        }

        if (phase.equals(PHASE_WATCHING) && !hasBrrWatchingState(additions)) {
            logger.warn("[BRR] Strategy {} - Invalid WATCHING state. Resetting.", strategy.getId());
            resetAdditions(additions);
            return;
        }

        if (phase.equals(PHASE_ENTRY) || phase.equals(PHASE_GAP)) {
            double h = additions.getBrrHigh();
            double l = additions.getBrrLow();

            boolean highBreak = (c2High + 1.0) > h;
            boolean lowBreak = (c2Low - 1.0) < l;

            if (highBreak && lowBreak) {
                logger.info("[BRR] Strategy {} - GAP detected. New reference: HIGH={}, LOW={}",
                        strategy.getId(), c2High, c2Low);
                additions.setBrrHigh(c2High);
                additions.setBrrLow(c2Low);
                additions.setBrrPhase(PHASE_GAP);
            } else if (highBreak) {
                logger.info("[BRR] Strategy {} - BUY Breakout! C2 High: {} > Ref High: {}",
                        strategy.getId(), c2High, h);
                strategy.setRemarks("BUY");
                additions.setBrrDirection(DIR_LONG);
                additions.setBrrPhase(PHASE_TRENDING);
                additions.setBrrHigh(c2High);
                additions.setBrrLow(c2Low);
                additions.setBrrStopLoss(c2Low);
                additions.setBrrResistance(null);
                additions.setBrrSupport(null);
                this.runStrategy(strategy);
            } else if (lowBreak) {
                logger.info("[BRR] Strategy {} - SELL Breakout! C2 Low: {} < Ref Low: {}",
                        strategy.getId(), c2Low, l);
                strategy.setRemarks("SELL");
                additions.setBrrDirection(DIR_SHORT);
                additions.setBrrPhase(PHASE_TRENDING);
                additions.setBrrLow(c2Low);
                additions.setBrrHigh(c2High);
                additions.setBrrStopLoss(c2High);
                additions.setBrrResistance(null);
                additions.setBrrSupport(null);
                this.runStrategy(strategy);
            } else {
                logger.info("[BRR] Strategy {} - No breakout. Moving to WATCHING. Res: {}, Sup: {}",
                        strategy.getId(), h, l);
                additions.setBrrResistance(h);
                additions.setBrrSupport(l);
                additions.setBrrPhase(PHASE_WATCHING);
            }
        } else if (phase.equals(PHASE_WATCHING)) {
            double res = additions.getBrrResistance();
            double sup = additions.getBrrSupport();

            if (c2High > res) {
                logger.info("[BRR] Strategy {} - WATCHING BUY Breakout! C2 High: {} > Res: {}",
                        strategy.getId(), c2High, res);
                strategy.setRemarks("BUY");
                additions.setBrrDirection(DIR_LONG);
                additions.setBrrPhase(PHASE_TRENDING);
                additions.setBrrHigh(c2High);
                additions.setBrrLow(c2Low);
                additions.setBrrStopLoss(c2Low);
                additions.setBrrResistance(null);
                additions.setBrrSupport(null);
                this.runStrategy(strategy);
            } else if (c2Low < sup) {
                logger.info("[BRR] Strategy {} - WATCHING SELL Breakout! C2 Low: {} < Sup: {}",
                        strategy.getId(), c2Low, sup);
                strategy.setRemarks("SELL");
                additions.setBrrDirection(DIR_SHORT);
                additions.setBrrPhase(PHASE_TRENDING);
                additions.setBrrLow(c2Low);
                additions.setBrrHigh(c2High);
                additions.setBrrStopLoss(c2High);
                additions.setBrrResistance(null);
                additions.setBrrSupport(null);
                this.runStrategy(strategy);
            } else {
                logger.debug("[BRR] Strategy {} - Still WATCHING. C2 High/Low: {}/{} within {}/{}",
                        strategy.getId(), c2High, c2Low, res, sup);
            }
        }

        additions.setBrrLastCandleTime(currTime);
        strategyAdditionsRepository.save(additions);
    }

    private Optional<CandleData> findFirstMarketCandle(List<CandleData> candles) {
        LocalDate today = LocalDate.now(ZONE_ID);
        return candles.stream()
                .filter(candle -> {
                    LocalDateTime candleTime = toFeedCandleZonedDateTime(candle.getEpochTime()).toLocalDateTime();
                    LocalTime candleLocalTime = candleTime.toLocalTime();
                    return today.equals(candleTime.toLocalDate())
                            && !candleLocalTime.isBefore(MARKET_OPEN_TIME)
                            && !candleLocalTime.isAfter(FIRST_CANDLE_CLOSE_TIME);
                })
                .min(Comparator.comparingLong(CandleData::getEpochTime));
    }

    private ZonedDateTime toFeedCandleZonedDateTime(long epochTime) {
        return Instant.ofEpochSecond(epochTime - IST_EPOCH_OFFSET_SECONDS).atZone(ZONE_ID);
    }

    private void processPhase2(Strategy strategy) {
        StrategyAdditions additions = strategy.getStrategyAdditions();
        List<CandleData> candles = getFuturesCandles(strategy, 3);
        if (candles == null || candles.size() < 1) {
            logger.warn("[BRR] Strategy {} - Insufficient candles for processPhase2", strategy.getId());
            return;
        }
        candles.sort(Comparator.comparingLong(CandleData::getEpochTime));
        CandleData currCandle = candles.get(candles.size() - 1);

        long currTime = currCandle.getEpochTime();
        if (additions.getBrrLastCandleTime() != null && additions.getBrrLastCandleTime() == currTime) {
            return;
        }

        double h = Math.max(currCandle.getOpen(), currCandle.getClose());
        double l = Math.min(currCandle.getOpen(), currCandle.getClose());
        Double sl = additions.getBrrStopLoss();

        boolean isLong = DIR_LONG.equals(additions.getBrrDirection());
        double buffer = additions.getBrrExitBuffer() != null ? additions.getBrrExitBuffer() : 1.0;

        logger.info("[BRR] Strategy {} Phase 2 - Direction: {}, Curr High/Low: {}/{}, SL: {}",
                strategy.getId(), additions.getBrrDirection(), h, l, sl);

        // 1. EXIT CHECK
        boolean slHit = sl != null && (isLong ? (l < sl) : (h > sl));
        if (slHit) {
            double exitPrice = isLong ? (l - buffer) : (h + buffer);
            logger.info("[BRR] Strategy {} - STOPLOSS hit ({}). Price {} vs SL {}. Exit: {}",
                    strategy.getId(), (isLong?"LONG":"SHORT"), (isLong?l:h), sl, exitPrice);

            this.exitStrategy(strategy, exitPrice);

            additions.setBrrHigh(h);
            additions.setBrrLow(l);
            additions.setBrrPhase(PHASE_ENTRY);
            additions.setBrrResistance(null);
            additions.setBrrSupport(null);
            additions.setBrrStopLoss(null);
            additions.setBrrDirection(null);
        } else {
            // 2. TREND CHECK
            if (isLong) {
                double previousHigh = additions.getBrrHigh();
                double previousLow = additions.getBrrLow();
                boolean highBreak = h > previousHigh;
                boolean lowBreak = l < previousLow;

                if (highBreak && lowBreak) {
                    logger.info("[BRR] Strategy {} - LONG gap/jobbing combo. Holding trend. High/Low broke previous {}/{}",
                            strategy.getId(), previousHigh, previousLow);
                    additions.setBrrHigh(h);
                    additions.setBrrLow(l);
                    additions.setBrrResistance(null);
                    additions.setBrrSupport(null);
                } else if (highBreak) {
                    logger.info("[BRR] Strategy {} - New High in LONG: {} > {}", strategy.getId(), h, previousHigh);
                    additions.setBrrHigh(h);
                    additions.setBrrLow(l);
                } else {
                    logger.info("[BRR] Strategy {} - Retracement in LONG. Res: {}, Sup: {}", strategy.getId(), previousHigh, l);
                    additions.setBrrResistance(previousHigh);
                    additions.setBrrPhase(PHASE_RETRACEMENT);
                    if (lowBreak) {
                        additions.setBrrSupport(null);
                        additions.setBrrLow(l);
                    } else {
                        additions.setBrrSupport(l);
                        additions.setBrrLow(l);
                    }
                }
            } else {
                // SHORT logic: track lower lows
                double previousLow = additions.getBrrLow();
                double previousHigh = additions.getBrrHigh();
                boolean lowBreak = l < previousLow;
                boolean highBreak = h > previousHigh;

                if (lowBreak && highBreak) {
                    logger.info("[BRR] Strategy {} - SHORT gap/jobbing combo. Holding trend. Low/High broke previous {}/{}",
                            strategy.getId(), previousLow, previousHigh);
                    additions.setBrrLow(l);
                    additions.setBrrHigh(h);
                    additions.setBrrResistance(null);
                    additions.setBrrSupport(null);
                } else if (lowBreak) {
                    logger.info("[BRR] Strategy {} - New Low in SHORT: {} < {}", strategy.getId(), l, previousLow);
                    additions.setBrrLow(l);
                    additions.setBrrHigh(h);
                } else {
                    logger.info("[BRR] Strategy {} - Retracement in SHORT. Sup: {}, Res: {}", strategy.getId(), previousLow, h);
                    additions.setBrrSupport(previousLow);
                    additions.setBrrPhase(PHASE_RETRACEMENT);
                    if (highBreak) {
                        additions.setBrrResistance(null);
                        additions.setBrrHigh(h);
                    } else {
                        additions.setBrrResistance(h);
                        additions.setBrrHigh(h);
                    }
                }
            }
        }

        additions.setBrrLastCandleTime(currTime);
        strategyAdditionsRepository.save(additions);
    }

    private void processPhase3(Strategy strategy) {
        StrategyAdditions additions = strategy.getStrategyAdditions();
        List<CandleData> candles = getFuturesCandles(strategy, 3);
        if (candles == null || candles.size() < 1) {
            logger.warn("[BRR] Strategy {} - Insufficient candles for processPhase3", strategy.getId());
            return;
        }
        candles.sort(Comparator.comparingLong(CandleData::getEpochTime));
        CandleData currCandle = candles.get(candles.size() - 1);

        long currTime = currCandle.getEpochTime();
        if (additions.getBrrLastCandleTime() != null && additions.getBrrLastCandleTime() == currTime) {
            return;
        }

        double h = Math.max(currCandle.getOpen(), currCandle.getClose());
        double l = Math.min(currCandle.getOpen(), currCandle.getClose());
        Double sl = additions.getBrrStopLoss();
        Double res = additions.getBrrResistance();
        Double sup = additions.getBrrSupport();

        boolean isLong = DIR_LONG.equals(additions.getBrrDirection());
        double buffer = additions.getBrrExitBuffer() != null ? additions.getBrrExitBuffer() : 1.0;

        logger.info("[BRR] Strategy {} Phase 3 - Direction: {}, Curr High/Low: {}/{}, SL: {}, Res: {}, Sup: {}",
                strategy.getId(), additions.getBrrDirection(), h, l, sl, res, sup);

        // 1. ALWAYS CHECK STOPLOSS FIRST
        boolean slHit = sl != null && (isLong ? (l < sl) : (h > sl));
        if (slHit) {
            double exitPrice = isLong ? (l - buffer) : (h + buffer);
            logger.info("[BRR] Strategy {} - STOPLOSS hit in PHASE_RETRACEMENT. Price {} vs SL {}. Exit: {}",
                    strategy.getId(), (isLong?l:h), sl, exitPrice);

            this.exitStrategy(strategy, exitPrice);

            additions.setBrrHigh(h);
            additions.setBrrLow(l);
            additions.setBrrPhase(PHASE_ENTRY);
            additions.setBrrResistance(null);
            additions.setBrrSupport(null);
            additions.setBrrStopLoss(null);
            additions.setBrrDirection(null);
        } else {
            if (isLong) {
                if (sup == null) {
                    if (l < additions.getBrrLow()) {
                        additions.setBrrLow(l);
                    } else {
                        logger.info("[BRR] Strategy {} - Floor found in LONG retracement: {}", strategy.getId(), l);
                        additions.setBrrSupport(l);
                        additions.setBrrLow(l);
                    }
                } else if (l < sup) {
                    logger.info("[BRR] Strategy {} - Floor broken in LONG retracement. Searching for new floor. Prev: {}", strategy.getId(), sup);
                    additions.setBrrSupport(null);
                    additions.setBrrLow(l);
                } else if (res != null && h > res) {
                    logger.info("[BRR] Strategy {} - LONG Breakout from Retracement! High: {} > Res: {}", strategy.getId(), h, res);
                    logger.info("[BRR] Strategy {} - Updating SL to new support: {}", strategy.getId(), sup);
                    additions.setBrrStopLoss(sup);
                    additions.setBrrPhase(PHASE_TRENDING);
                    additions.setBrrHigh(h);
                    additions.setBrrLow(l);
                    additions.setBrrResistance(null);
                    additions.setBrrSupport(null);
                } else {
                    additions.setBrrLow(l);
                }
            } else {
                // SHORT Phase 3: Searching for ceiling (Resistance) during bounce

                if (res == null) {
                    if (h > additions.getBrrHigh()) {
                        additions.setBrrHigh(h);
                    } else {
                        logger.info("[BRR] Strategy {} - Ceiling found in SHORT retracement: {}", strategy.getId(), h);
                        additions.setBrrResistance(h);
                        additions.setBrrHigh(h);
                    }
                } else if (h > res) {
                    logger.info("[BRR] Strategy {} - Ceiling broken in SHORT retracement. Searching for new ceiling. Prev: {}", strategy.getId(), res);
                    additions.setBrrResistance(null);
                    additions.setBrrHigh(h);
                } else if (sup != null && l < sup) {
                    logger.info("[BRR] Strategy {} - SHORT Breakout from Retracement! Low: {} < Sup: {}", strategy.getId(), l, sup);
                    logger.info("[BRR] Strategy {} - Updating SL to new resistance: {}", strategy.getId(), res);
                    additions.setBrrStopLoss(res);
                    additions.setBrrPhase(PHASE_TRENDING);
                    additions.setBrrLow(l);
                    additions.setBrrHigh(h);
                    additions.setBrrResistance(null);
                    additions.setBrrSupport(null);
                } else {
                    additions.setBrrHigh(h);
                }
            }
        }

        additions.setBrrLastCandleTime(currTime);
        strategyAdditionsRepository.save(additions);
    }
    private List<CandleData> getFuturesCandles(Strategy strategy, int count) {

        try {
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);

            String futuresExpiry = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.FUTURE.getKey());

            String futureKey = underlying + futuresExpiry + OptionType.FUTURE.getKey();

            MasterResponseFO futuresMaster = marketDataFetch.getMasterResponse(futureKey);

            if (futuresMaster == null) {
                logger.warn("No futures master found for {}", futureKey);
                return null;
            }

            String redisKey = underlying + ":" + futuresMaster.getExchangeInstrumentID();

            logger.info(
                    "Fetching futures candles | FutureKey={} | RedisKey={}",
                    futureKey,
                    redisKey
            );

            return candleRedisService.getCandles(redisKey, "15m", count);

        } catch (Exception e) {
            logger.error("Error fetching futures candles", e);
            return null;
        }
    }

    private void logBhuvasSignalData(Strategy strategy, Signal signal, String signalType) {
        try {
            StrategyAdditions additions = strategy.getStrategyAdditions();
            StringBuilder description = new StringBuilder();
            description.append("Bhuvas Trend Following Signal Created - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Instrument: ").append(strategy.getUnderlying().getName()).append("\n");

            if (additions != null) {
                description.append("Phase: ").append(additions.getBrrPhase()).append("\n");
                description.append("Direction: ").append(additions.getBrrDirection()).append("\n");
                description.append("Reference High: ").append(additions.getBrrHigh()).append("\n");
                description.append("Reference Low: ").append(additions.getBrrLow()).append("\n");
                description.append("Stop Loss: ").append(additions.getBrrStopLoss()).append("\n");
            }

            description.append("Signal Type: ").append(signalType).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.LIVE.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            logger.info(description.toString());
        } catch (Exception e) {
            logger.error("Error logging signal data for BhuvasTrendFollowingStrategy | StrategyId={}", strategy.getId(), e);
        }
    }

    private void logBhuvasExitData(Strategy strategy, String reason, Double exitPrice) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("Bhuvas Trend Following Exit Triggered - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Instrument: ").append(strategy.getUnderlying().getName()).append("\n");
            description.append("Reason: ").append(reason).append("\n");
            description.append("Exit Price: ").append(exitPrice != null ? exitPrice : "N/A").append("\n");
            description.append("Exit Time: ").append(Instant.now()).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.EXIT.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            logger.info(description.toString());
        } catch (Exception e) {
            logger.error("Error logging exit data for BhuvasTrendFollowingStrategy | StrategyId={}", strategy.getId(), e);
        }
    }
}