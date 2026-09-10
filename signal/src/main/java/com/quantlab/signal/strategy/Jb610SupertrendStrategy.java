package com.quantlab.signal.strategy;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.IndexDifference;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.Jb610DetectionResult;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.engine.Jb610EngineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.Instant;
import java.util.*;

/**
 * JB610 Dual Supertrend Crossover Strategy
 *
 * Instrument: BANKNIFTY (30-min INDEX candles)
 * Dual Supertrend: ST(6,1) fast + ST(10,3) slow
 *
 * Signal Interpretation:
 * - BULLISH (fast crosses above slow):
 *   Bull Put Spread — Sell Put 2% below spot, Buy Put 3% below spot
 *
 * - BEARISH (fast crosses below slow):
 *   Bear Call Spread — Sell Call 2% above spot, Buy Call 3% above spot
 *
 * Strike selection uses conservative rounding:
 *   - Sell strike rounded towards spot (floor for puts, ceil for calls)
 *   - Buy strike rounded away from spot (floor for puts further OTM, ceil for calls further OTM)
 *
 * Exit: Daily ST(6,1) breach (direction flip on daily candle close), or on expiry.
 *   No intraday stop-loss — only daily closing confirmation.
 *
 * Expiry: Day 1-20 → current month, Day 21+ → next month (dynamic selection)
 */
@Service("Jb610SupertrendStrategy")
public class Jb610SupertrendStrategy implements StrategiesImplementation<Jb610SupertrendStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(Jb610SupertrendStrategy.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Strike percentages for credit spreads
    private static final double SELL_OFFSET_PCT = 0.02;  // 2% from spot
    private static final double BUY_OFFSET_PCT = 0.03;   // 3% from spot

    // Expiry selection threshold: Day 1-20 → currentMonth, Day 21+ → nextMonth
    private static final int EXPIRY_ROLLOVER_DAY = 20;

    @Autowired
    private Jb610EngineService engineService;

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
    private SignalRepository signalRepository;

    @Autowired
    private DeploymentErrorsRepository deploymentErrorsRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        try {
            // logger.info("[JB610] check() START | StrategyId={} | Status={} | ManualExit={} | ExecutionType={}",
            //         strategy.getId(), strategy.getStatus(), strategy.getManualExitType(), strategy.getExecutionType());

            Strategy fresh = strategyRepository.findByIdWithAdditions(strategy.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Strategy not found"));

            // logger.info("[JB610] Fresh strategy loaded | StrategyId={} | FreshStatus={} | FreshManualExit={} | ReSignalCount={} | SignalCount={}",
            //         fresh.getId(), fresh.getStatus(), fresh.getManualExitType(),
            //         fresh.getReSignalCount(), fresh.getSignalCount());

            // === ENTRY PATH ===
            boolean entryCheck = strategyService.inHouseEntryCheck(fresh);
            // logger.info("[JB610] inHouseEntryCheck result={} | StrategyId={}", entryCheck, fresh.getId());

            if (entryCheck) {
                // logger.info("[JB610] Entering runStrategy | StrategyId={}", fresh.getId());
                runStrategy(fresh);
            }
            // === EXIT PATH ===
            else if (fresh.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                if (LocalTime.now(IST).isAfter(LocalTime.of(9, 15))) {
                    // logger.info("[JB610] Strategy is LIVE, checking exit | StrategyId={}", fresh.getId());
                    if (checkExit(fresh)) {
                        this.exitStrategy(fresh);
                    }
                } else {
                    // logger.info("[JB610] Strategy is LIVE, but skipping exit check before 09:15 | StrategyId={}", fresh.getId());
                }
            } else {
                // logger.info("[JB610] No action taken | StrategyId={} | Status={} | entryCheck=false",
                //         fresh.getId(), fresh.getStatus());
            }
        } catch (Exception e) {
            logger.error("[JB610] Error in check() | StrategyId={}", strategy.getId(), e);
        }
    }

    @Override
    public Signal runStrategy(Strategy strategy) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase();
            // logger.info("[JB610][{}] runStrategy START | StrategyId={} | Status={} | Expiry={} | Multiplier={} | PositionType={}",
            //         underlying, strategy.getId(), strategy.getStatus(), strategy.getExpiry(),
            //         strategy.getMultiplier(), strategy.getPositionType());

            // Read detection result from Redis
            Optional<Jb610DetectionResult> resultOpt = engineService.getSignal(underlying);

            if (resultOpt.isEmpty()) {
                // logger.info("[JB610][{}] BLOCKED: No detection result in Redis | StrategyId={}", underlying, strategy.getId());
                return null;
            }

            Jb610DetectionResult result = resultOpt.get();
            // logger.info("[JB610][{}] Detection result from Redis | Direction={} | isBullish={} | shouldTriggerEntry={} | FastST={} | SlowST={} | SpotPrice={} | CandleEpoch={} | StrategyId={}",
            //         underlying, result.getDirection(), result.isBullish(), result.shouldTriggerEntry(),
            //         String.format("%.2f", result.getFastSupertrendValue()),
            //         String.format("%.2f", result.getSlowSupertrendValue()),
            //         String.format("%.2f", result.getSpotPrice()),
            //         result.getCandleEpochTime(),
            //         strategy.getId());

            if (!result.shouldTriggerEntry()) {
                // logger.info("[JB610][{}] BLOCKED: shouldTriggerEntry=false, no crossover detected | Direction={} | FastST={} | SlowST={} | StrategyId={}",
                //         underlying, result.getDirection(),
                //         String.format("%.2f", result.getFastSupertrendValue()),
                //         String.format("%.2f", result.getSlowSupertrendValue()),
                //         strategy.getId());
                return null;
            }

            // Deduplication: prevent re-entry on same candle
            Hibernate.initialize(strategy.getStrategyAdditions());
            StrategyAdditions additions = strategy.getStrategyAdditions();
            if (additions == null) {
                // logger.info("[JB610][{}] StrategyAdditions is null, creating new | StrategyId={}", underlying, strategy.getId());
                additions = new StrategyAdditions();
                additions.setStrategy(strategy);
                strategy.setStrategyAdditions(additions);
            }

            Long lastProcessedEpoch = additions.getLastAccumulation15mBarTime();
            long candleEpoch = result.getCandleEpochTime();
            // logger.info("[JB610][{}] Dedup check | CandleEpoch={} | LastProcessedEpoch={} | Diff={} | StrategyId={}",
            //         underlying, candleEpoch, lastProcessedEpoch,
            //         lastProcessedEpoch != null ? Math.abs(candleEpoch - lastProcessedEpoch / 1000) : "N/A",
            //         strategy.getId());

            if (lastProcessedEpoch != null && candleEpoch > 0 &&
                    Math.abs(candleEpoch - lastProcessedEpoch / 1000) <= 60) {
                // logger.info("[JB610][{}] BLOCKED: Duplicate candle prevented | CandleEpoch={} | LastEpoch={} | Diff={}s | StrategyId={}",
                //         underlying, candleEpoch, lastProcessedEpoch,
                //         Math.abs(candleEpoch - lastProcessedEpoch / 1000),
                //         strategy.getId());
                return null;
            }

            boolean dailyBreached = engineService.isDailySupertrendBreached(underlying);
            if (dailyBreached) {
                additions.setLastAccumulation15mBarTime(candleEpoch * 1000);
                strategyRepository.save(strategy);

                logger.info("[JB610][{}] ENTRY BLOCKED: Daily ST(6,1) breach already active | CandleEpoch={} | StrategyId={}",
                        underlying, candleEpoch, strategy.getId());
                return null;
            }

            // Create credit spread signal
            // logger.info("[JB610][{}] Proceeding to create spread | isBullish={} | StrategyId={}", underlying, result.isBullish(), strategy.getId());
            Signal signal;
            if (result.isBullish()) {
                signal = createBullPutSpread(strategy, result);
            } else {
                signal = createBearCallSpread(strategy, result);
            }

            if (signal != null) {
                // Update dedup tracking
                additions.setLastAccumulation15mBarTime(candleEpoch * 1000);
                strategyRepository.save(strategy);

                logger.info("[JB610][{}] Signal created SUCCESS | SignalId={} | Direction={} | Spot={} | StrategyId={}",
                        underlying, signal.getId(), result.getDirection(),
                        String.format("%.2f", result.getSpotPrice()), strategy.getId());
                if (ExecutionTypeMenu.PAPER_TRADING.getKey()
                        .equalsIgnoreCase(strategy.getExecutionType())) {
                    logJb610SignalData(strategy, result, signal);
                }
            } else {
                logger.warn("[JB610][{}] BLOCKED: Signal creation returned null | StrategyId={}", underlying, strategy.getId());
            }

            return signal;

        } catch (Exception e) {
            logger.error("[JB610] Error in runStrategy | StrategyId={}", strategy.getId(), e);
            signalService.errorCreatingSignal(strategy, e);
            return null;
        }
    }

    /**
     * Bull Put Spread (BULLISH signal):
     * - Sell Put at strike = floor(spot * 0.98 / step) * step  (2% below, round DOWN)
     * - Buy Put at strike = floor(spot * 0.97 / step) * step   (3% below, round DOWN)
     */
    private Signal createBullPutSpread(Strategy strategy, Jb610DetectionResult result) {
        Hibernate.initialize(strategy.getUnderlying());
        String underlying = strategy.getUnderlying().getName().toUpperCase();
        double spotPrice = result.getSpotPrice();
        int strikeStep = IndexDifference.fromKey(underlying).getLabel();

        // Conservative rounding: floor for puts below spot
        int sellStrike = (int) (Math.floor(spotPrice * (1.0 - SELL_OFFSET_PCT) / strikeStep) * strikeStep);
        int buyStrike = (int) (Math.floor(spotPrice * (1.0 - BUY_OFFSET_PCT) / strikeStep) * strikeStep);

        logger.info("[JB610][{}] Bull Put Spread | Spot={} | SellPut={} | BuyPut={}",
                strategy.getId(), String.format("%.2f", spotPrice), sellStrike, buyStrike);

        // Dynamic expiry: Day 1-20 → currentMonth, Day 21+ → nextMonth
        String expiryKey = resolveExpiryKey();
        String expiryDate = commonUtils.getExpiryShotDateByIndex(
                expiryKey, underlying, OptionType.OPTION.getKey());

        if (expiryDate == null) {
            logger.error("[JB610] BLOCKED: Expiry date is null | ExpiryKey={} | Underlying={} | StrategyId={}", expiryKey, underlying, strategy.getId());
            return null;
        }
        logger.info("[JB610] Bull Put Spread expiry resolved | ExpiryKey={} | ExpiryDate={} | StrategyId={}", expiryKey, expiryDate, strategy.getId());

        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
        List<SignalMapperDto> legs = new ArrayList<>();

        // Leg 1: Sell Put (higher strike, closer to spot)
        legs.add(buildLeg(strategy, underlying, expiryDate, sellStrike, "PE",
                LegSide.SELL.getKey(), LegType.PUT.getKey(), marketLive));

        // Leg 2: Buy Put (lower strike, further from spot — protection)
        legs.add(buildLeg(strategy, underlying, expiryDate, buyStrike, "PE",
                LegSide.BUY.getKey(), LegType.PUT.getKey(), marketLive));

        return executeSignal(strategy, legs);
    }

    /**
     * Bear Call Spread (BEARISH signal):
     * - Sell Call at strike = ceil(spot * 1.02 / step) * step  (2% above, round UP)
     * - Buy Call at strike = ceil(spot * 1.03 / step) * step   (3% above, round UP)
     */
    private Signal createBearCallSpread(Strategy strategy, Jb610DetectionResult result) {
        Hibernate.initialize(strategy.getUnderlying());
        String underlying = strategy.getUnderlying().getName().toUpperCase();
        double spotPrice = result.getSpotPrice();
        int strikeStep = IndexDifference.fromKey(underlying).getLabel();

        // Conservative rounding: ceil for calls above spot
        int sellStrike = (int) (Math.ceil(spotPrice * (1.0 + SELL_OFFSET_PCT) / strikeStep) * strikeStep);
        int buyStrike = (int) (Math.ceil(spotPrice * (1.0 + BUY_OFFSET_PCT) / strikeStep) * strikeStep);

        logger.info("[JB610][{}] Bear Call Spread | Spot={} | SellCall={} | BuyCall={}",
                strategy.getId(), String.format("%.2f", spotPrice), sellStrike, buyStrike);

        // Dynamic expiry: Day 1-20 → currentMonth, Day 21+ → nextMonth
        String expiryKey = resolveExpiryKey();
        String expiryDate = commonUtils.getExpiryShotDateByIndex(
                expiryKey, underlying, OptionType.OPTION.getKey());

        if (expiryDate == null) {
            logger.error("[JB610] BLOCKED: Expiry date is null | ExpiryKey={} | Underlying={} | StrategyId={}", expiryKey, underlying, strategy.getId());
            return null;
        }
        logger.info("[JB610] Bear Call Spread expiry resolved | ExpiryKey={} | ExpiryDate={} | StrategyId={}", expiryKey, expiryDate, strategy.getId());

        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
        List<SignalMapperDto> legs = new ArrayList<>();

        // Leg 1: Sell Call (lower strike, closer to spot)
        legs.add(buildLeg(strategy, underlying, expiryDate, sellStrike, "CE",
                LegSide.SELL.getKey(), LegType.CALL.getKey(), marketLive));

        // Leg 2: Buy Call (higher strike, further from spot — protection)
        legs.add(buildLeg(strategy, underlying, expiryDate, buyStrike, "CE",
                LegSide.BUY.getKey(), LegType.CALL.getKey(), marketLive));

        return executeSignal(strategy, legs);
    }

    /**
     * Build a single leg DTO for signal creation.
     */
    private SignalMapperDto buildLeg(Strategy strategy, String underlying, String expiryDate,
                                     int strike, String optionSuffix, String buySell,
                                     String category, MarketLiveDto marketLive) {
        String key = underlying + expiryDate + "-" + strike + optionSuffix;
        // logger.info("[JB610] buildLeg | Key={} | BuySell={} | Category={} | StrategyId={}", key, buySell, category, strategy.getId());

        MasterResponseFO master = marketDataFetch.getMasterResponse(key);
        if (master == null) {
            logger.error("[JB610] BLOCKED: Master not found | Key={} | StrategyId={}", key, strategy.getId());
            throw new RuntimeException("Master data not found for key: " + key);
        }
        // logger.info("[JB610] Master found | Key={} | InstrumentId={} | LotSize={} | StrategyId={}",
        //         key, master.getExchangeInstrumentID(), master.getLotSize(), strategy.getId());

        MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
        if (data == null) {
            logger.error("[JB610] BLOCKED: Touchline not found | Key={} | InstrumentId={} | StrategyId={}",
                    key, master.getExchangeInstrumentID(), strategy.getId());
            throw new RuntimeException("Touchline data not found for instrument: " + master.getExchangeInstrumentID());
        }
        // logger.info("[JB610] Touchline found | Key={} | InstrumentId={} | StrategyId={}", key, master.getExchangeInstrumentID(), strategy.getId());

        SignalMapperDto leg = new SignalMapperDto();
        leg.setMarketLiveDto(marketLive);
        leg.setTouchlineBinaryResponse(data);
        leg.setLegName(key);
        leg.setMasterData(master);
        leg.setBuySellFlag(buySell);
        leg.setSegment("NSEFO");
        leg.setCategory(category);
        leg.setPositionType(strategy.getPositionType());
        leg.setLegType(LegType.OPEN.getKey());
        leg.setDerivativeType(OptionType.OPTION.getKey());
        leg.setLots(1L);
        leg.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));
        // logger.info("[JB610] Leg built | Key={} | Quantity={} | StrategyId={}", key, leg.getQuantity(), strategy.getId());

        return leg;
    }

    /**
     * Create signal and send to exchange.
     */
    private Signal executeSignal(Strategy strategy, List<SignalMapperDto> legs) {
        // logger.info("[JB610] executeSignal START | StrategyId={} | LegCount={} | ExecutionType={}",
        //         strategy.getId(), legs.size(), strategy.getExecutionType());

        Signal signal = signalService.createSignal(strategy, legs);
        // logger.info("[JB610] Signal created by signalService | SignalId={} | StrategyId={}", signal.getId(), strategy.getId());

        strategyRepository.updateSignalCount(strategy.getId());

        signal = signalRepository.findById(signal.getId()).orElseThrow();
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
            grpcService.sendSignal(signal);
            logger.info("[JB610] Live trading signal sent | SignalId={} | StrategyId={}", signal.getId(), strategy.getId());
        } else {
            // logger.info("[JB610] Paper trading mode, skipping gRPC | SignalId={} | StrategyId={}", signal.getId(), strategy.getId());
        }

        return signal;
    }

    /**
     * Check exit conditions (per strategy spec):
     * 1. Manual exit (admin override)
     * 2. Expiry day — options expire, exit position from system
     * 3. Daily ST(6,1) breach — direction flip on daily candle close
     * No intraday stop-loss. Only daily closing confirmation.
     */
    private boolean checkExit(Strategy strategy) {
        try {
            // logger.info("[JB610] checkExit START | StrategyId={} | ManualExit={}", strategy.getId(), strategy.getManualExitType());

            // 1. Manual exit
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                logger.info("[JB610] EXIT: Manual exit | StrategyId={}", strategy.getId());
                logExitSignalData(strategy, "Manual Exit Triggered");
                return true;
            }

            // 2. Expiry day — options expire naturally, exit position from system
            if (commonUtils.isExpiryDay(strategy)) {
                logger.info("[JB610] EXIT: Expiry day reached | StrategyId={}", strategy.getId());
                logExitSignalData(strategy, "Expiry Day Reached");
                return true;
            }

            // 3. Daily ST(6,1) breach
            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase();

            // logger.info("[JB610] Checking daily ST(6,1) breach | StrategyId={} | Underlying={}", strategy.getId(), underlying);
            boolean breached = engineService.isDailySupertrendBreached(underlying);

            if (breached) {
                logger.info("[JB610] EXIT: Daily ST(6,1) breached on daily chart | StrategyId={} | Underlying={}", strategy.getId(), underlying);
                logExitSignalData(strategy, "Daily ST(6,1) Breach");
                return true;
            }

            // logger.info("[JB610] No exit condition met | StrategyId={} | Underlying={}", strategy.getId(), underlying);
            return false;

        } catch (Exception e) {
            logger.error("[JB610] Error checking exit | StrategyId={}", strategy.getId(), e);
            return false;
        }
    }

    /**
     * Dynamic expiry selection based on day of month (per strategy spec):
     * - Day 1 to 20  → "currentMonth" (current month expiry)
     * - Day 21 to end → "nextMonth"    (next month expiry)
     *
     * This avoids holding positions too close to expiry where time decay
     * accelerates and liquidity may drop.
     */
    private String resolveExpiryKey() {
        int dayOfMonth = LocalDate.now(IST).getDayOfMonth();
        String expiryKey = dayOfMonth <= EXPIRY_ROLLOVER_DAY ? "currentMonth" : "nextMonth";
        // logger.info("[JB610] Expiry resolved | DayOfMonth={} | ExpiryKey={}", dayOfMonth, expiryKey);
        return expiryKey;
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            // logger.info("[JB610] exitStrategy START | StrategyId={} | ExecutionType={}", strategy.getId(), strategy.getExecutionType());

            Signal exit = signalService.createExit(strategy);
            if (exit == null) {
                logger.warn("[JB610] BLOCKED: createExit returned null — no open signal to exit | StrategyId={}", strategy.getId());
                return;
            }
            logger.info("[JB610] Exit signal created | ExitSignalId={} | StrategyId={}", exit.getId(), strategy.getId());

            if (!ExecutionTypeMenu.PAPER_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                grpcService.sendExitSignal(exit);
                logger.info("[JB610] Live trading exit signal sent | ExitSignalId={} | StrategyId={}", exit.getId(), strategy.getId());
            } else {
                // logger.info("[JB610] Paper trading mode, skipping gRPC exit | ExitSignalId={} | StrategyId={}", exit.getId(), strategy.getId());
            }
        } catch (Exception ex) {
            logger.error("[JB610] CRITICAL: Error exiting strategy | StrategyId={}", strategy.getId(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void logJb610SignalData(Strategy strategy, Jb610DetectionResult result, Signal signal) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("JB610 Signal Created - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Instrument: ").append(strategy.getUnderlying().getName()).append("\n");
            description.append("Direction: ").append(result.getDirection()).append("\n");
            description.append("Spot Price: ").append(String.format("%.2f", result.getSpotPrice())).append("\n");
            description.append("Fast ST: ").append(String.format("%.2f", result.getFastSupertrendValue())).append("\n");
            description.append("Slow ST: ").append(String.format("%.2f", result.getSlowSupertrendValue())).append("\n");
            description.append("Candle Time (Epoch): ").append(result.getCandleEpochTime()).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.LIVE.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            logger.info(description.toString());

        } catch (Exception e) {
            logger.error("[JB610] Error logging signal data", e);
        }
    }

    private void logExitSignalData(Strategy strategy, String reason) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("JB610 Exit Triggered - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Instrument: ").append(strategy.getUnderlying().getName()).append("\n");
            description.append("Reason: ").append(reason).append("\n");
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
            logger.error("[JB610] Error logging exit data", e);
        }
    }
}