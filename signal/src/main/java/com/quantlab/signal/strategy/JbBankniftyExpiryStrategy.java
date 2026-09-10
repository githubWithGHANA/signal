package com.quantlab.signal.strategy;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.IndexDifference;
import com.quantlab.signal.dto.JbBankniftyExpiryDetectionResult;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.RedisLegState;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.LegStateProvider;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.RedisLegService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.engine.JbBankniftyExpiryEngineService;
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
 * JB Banknifty Expiry Special Strategy
 *
 * Structure: Short ATM Straddle on BANKNIFTY (Expiry day only)
 * Timeframe: 15 Min
 * Indicator: RSI on ATM Straddle combined premium (CE + PE)
 * Overlay: Supertrend (10,3)
 *
 * Entry: Sell ATM Straddle when RSI < 30 on 15-min chart
 * Hold: While price closes below Supertrend OR before 3:00 PM
 * Hedge: If Supertrend violated, buy protection based on premium dominance
 * Exit: Rs 3000 profit OR 3:14 PM OR 2.5% capital return
 *
 * Constraints: One trade at a time, no re-entry after exit, intraday only
 */
@Service("JbBankniftyExpiryStrategy")
public class JbBankniftyExpiryStrategy implements StrategiesImplementation<JbBankniftyExpiryStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(JbBankniftyExpiryStrategy.class);

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    // Exit parameters
    private static final double BASKET_PROFIT_TARGET = 3000.0;    // Rs 3000 net profit per basket
    private static final double CAPITAL_RETURN_PCT = 2.5;          // 2.5% return on deployed capital
    private static final LocalTime HOLD_CUTOFF_TIME = LocalTime.of(15, 0);   // 3:00 PM
    private static final LocalTime HARD_EXIT_TIME = LocalTime.of(15, 14);    // 3:14 PM hard exit

    // Strike step resolved dynamically per underlying via IndexDifference

    @Autowired
    private JbBankniftyExpiryEngineService engineService;

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
    private StrategyLegRepository strategyLegRepository;

    @Autowired
    private LegStateProvider legStateProvider;

    @Autowired
    private RedisLegService redisLegService;

    @Autowired
    private StrategyAdditionsRepository strategyAdditionsRepository;

    @Autowired
    private DeploymentErrorsRepository deploymentErrorsRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        try {
            Strategy fresh = strategyRepository.findByIdWithAdditions(strategy.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Strategy not found"));

            // === ENTRY PATH ===
            if (strategyService.inHouseEntryCheck(fresh)) {
                runStrategy(fresh);
            }
            // === EXIT PATH ===
            else if (fresh.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                if (checkExit(fresh)) {
                    this.exitStrategy(fresh);
                }
            }
        } catch (Exception e) {
            logger.error("[JB-EXPIRY] Error in check() | StrategyId={}", strategy.getId(), e);
        }
    }

    @Override
    public Signal runStrategy(Strategy strategy) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase();

            // Read detection result from Redis
            Optional<JbBankniftyExpiryDetectionResult> resultOpt = engineService.getSignal(underlying);

            if (resultOpt.isEmpty()) {
                // logger.info("[JB-EXPIRY][{}] No detection result | StrategyId={}", underlying, strategy.getId());
                return null;
            }

            JbBankniftyExpiryDetectionResult result = resultOpt.get();

            if (!result.shouldTriggerEntry()) {
                // logger.info("[JB-EXPIRY][{}] Entry not triggered | RSI={} | RsiTriggered={} | StrategyId={}",
                //         underlying, String.format("%.2f", result.getRsiValue()),
                //         result.isRsiSignalTriggered(), strategy.getId());
                return null;
            }

            // Deduplication: prevent re-entry
            Hibernate.initialize(strategy.getStrategyAdditions());
            StrategyAdditions additions = strategy.getStrategyAdditions();
            if (additions == null) {
                additions = new StrategyAdditions();
                additions.setStrategy(strategy);
                strategy.setStrategyAdditions(additions);
            }

            Long lastProcessedEpoch = additions.getLastAccumulation15mBarTime();
            long candleEpoch = result.getCandleEpochTime();

            if (lastProcessedEpoch != null && candleEpoch > 0 &&
                    Math.abs(candleEpoch - lastProcessedEpoch / 1000) <= 30) {
                // logger.info("[JB-EXPIRY][{}] Duplicate prevented | CandleEpoch={} | LastEpoch={} | StrategyId={}",
                //         underlying, candleEpoch, lastProcessedEpoch, strategy.getId());
                return null;
            }

            // Create short straddle signal
            Signal signal = createShortStraddleSignal(strategy, result);

            if (signal != null) {
                // Update dedup tracking
                additions.setLastAccumulation15mBarTime(candleEpoch * 1000);
                strategyRepository.save(strategy);

                logger.info("[JB-EXPIRY][{}] Short straddle created | SignalId={} | ATM={} | RSI={} | StrategyId={}",
                        underlying, signal.getId(), result.getAtmStrike(),
                        String.format("%.2f", result.getRsiValue()), strategy.getId());
                logJbExpirySignalData(strategy, result, signal);
            } else {
                logger.warn("[JB-EXPIRY][{}] createShortStraddleSignal returned null | StrategyId={}", underlying, strategy.getId());
            }

            return signal;

        } catch (Exception e) {
            logger.error("[JB-EXPIRY] Error in runStrategy | StrategyId={}", strategy.getId(), e);
            signalService.errorCreatingSignal(strategy, e);
            return null;
        }
    }

    /**
     * Create short ATM straddle: Sell ATM CE + Sell ATM PE
     */
    private Signal createShortStraddleSignal(Strategy strategy, JbBankniftyExpiryDetectionResult result) {
        Hibernate.initialize(strategy.getUnderlying());
        String underlying = strategy.getUnderlying().getName().toUpperCase();
        int atmStrike = result.getAtmStrike();

        String expiryDate = commonUtils.getExpiryShotDateByIndex(
                strategy.getExpiry(), underlying,
                com.quantlab.common.utils.staticstore.dropdownutils.OptionType.OPTION.getKey());

        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

        List<SignalMapperDto> legs = new ArrayList<>();

        // Leg 1: Sell ATM CE
        {
            String key = underlying + expiryDate + "-" + atmStrike + "CE";
            MasterResponseFO master = marketDataFetch.getMasterResponse(key);
            if (master == null) {
                logger.error("[JB-EXPIRY] Master not found for CE | Key={}", key);
                throw new RuntimeException("Master data not found for CE key: " + key);
            }
            MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
            if (data == null) {
                logger.error("[JB-EXPIRY] Touchline not found for CE | InstrumentId={}", master.getExchangeInstrumentID());
                throw new RuntimeException("Touchline not found for CE instrument: " + master.getExchangeInstrumentID());
            }

            SignalMapperDto ce = new SignalMapperDto();
            ce.setMarketLiveDto(marketLive);
            ce.setTouchlineBinaryResponse(data);
            ce.setLegName(key);
            ce.setMasterData(master);
            ce.setBuySellFlag(LegSide.SELL.getKey());
            ce.setSegment("NSEFO");
            ce.setCategory(LegType.CALL.getKey());
            ce.setPositionType(strategy.getPositionType());
            ce.setLegType(LegType.OPEN.getKey());
            ce.setDerivativeType(
                    com.quantlab.common.utils.staticstore.dropdownutils.OptionType.OPTION.getKey());
            ce.setLots(1L);
            ce.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

            legs.add(ce);
        }

        // Leg 2: Sell ATM PE
        {
            String key = underlying + expiryDate + "-" + atmStrike + "PE";
            MasterResponseFO master = marketDataFetch.getMasterResponse(key);
            if (master == null) {
                logger.error("[JB-EXPIRY] Master not found for PE | Key={}", key);
                throw new RuntimeException("Master data not found for PE key: " + key);
            }
            MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
            if (data == null) {
                logger.error("[JB-EXPIRY] Touchline not found for PE | InstrumentId={}", master.getExchangeInstrumentID());
                throw new RuntimeException("Touchline not found for PE instrument: " + master.getExchangeInstrumentID());
            }

            SignalMapperDto pe = new SignalMapperDto();
            pe.setMarketLiveDto(marketLive);
            pe.setTouchlineBinaryResponse(data);
            pe.setLegName(key);
            pe.setMasterData(master);
            pe.setBuySellFlag(LegSide.SELL.getKey());
            pe.setSegment("NSEFO");
            pe.setCategory(LegType.PUT.getKey());
            pe.setPositionType(strategy.getPositionType());
            pe.setLegType(LegType.OPEN.getKey());
            pe.setDerivativeType(
                    com.quantlab.common.utils.staticstore.dropdownutils.OptionType.OPTION.getKey());
            pe.setLots(1L);
            pe.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

            legs.add(pe);
        }

        Signal signal = signalService.createSignal(strategy, legs);
        strategyRepository.updateSignalCount(strategy.getId());

        // Send to exchange if not paper trading
        signal = signalRepository.findById(signal.getId()).orElseThrow();
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
            grpcService.sendSignal(signal);
        }

        return signal;
    }

    /**
     * Check all exit conditions for the short straddle basket.
     *
     * Exit when:
     * 1. Rs 3000 net profit per basket
     * 2. 3:14 PM hard time exit
     * 3. 2.5% capital return on deployed capital (anytime)
     *
     * Additionally monitors Supertrend violation for hedge addition.
     */
    private boolean checkExit(Strategy strategy) {
        try {
            // === Manual Exit ===
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                logger.info("[JB-EXPIRY] Manual exit | StrategyId={}", strategy.getId());
                logJbExpiryExitData(strategy, "Manual Exit Triggered");
                return true;
            }

            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase();

            LocalTime nowIST = LocalTime.now(ZONE_ID);

            // === Hard Time Exit at 3:14 PM ===
            if (!nowIST.isBefore(HARD_EXIT_TIME)) {
                logger.info("[JB-EXPIRY] Hard exit time | Time={} | StrategyId={}", nowIST, strategy.getId());
                logJbExpiryExitData(strategy, "Hard Exit Time Reached");
                return true;
            }

            // === Fetch Active Signal & Legs ===
            List<Signal> activeSignals = signalRepository.findByStrategyIdAndStatus(strategy.getId(), Status.LIVE.getKey());
            if (activeSignals.isEmpty()) {
                return false;
            }

            activeSignals.sort(Comparator.comparingLong(Signal::getId));
            Optional<Signal> signalOpt = signalRepository.findByIdForUpdate(activeSignals.get(0).getId());
            if (signalOpt.isEmpty()) {
                logger.warn("[JB-EXPIRY] Signal not found for update | SignalId={}", activeSignals.get(0).getId());
                return false;
            }

            Signal signal = signalOpt.get();
            Hibernate.initialize(signal.getSignalLegs());
            List<StrategyLeg> legs = signal.getSignalLegs();

            // Calculate basket P&L across all legs
            double totalPnl = 0.0;
            double totalDeployed = strategy.getMinCapital();
            boolean hasHedgeLeg = false;

            for (StrategyLeg leg : legs) {
                if (!Status.LIVE.getKey().equalsIgnoreCase(leg.getStatus())) continue;

                RedisLegState state = legStateProvider.getLegState(strategy.getId(), leg.getId());
                if (state == null || state.getExecutedPrice() == null) {
                    logger.warn("[JB-EXPIRY] LegState missing | LegId={} | StrategyId={}", leg.getId(), strategy.getId());
                    continue;
                }

                MarketData tick = touchLineService.getTouchLine(String.valueOf(leg.getExchangeInstrumentId()));
                if (tick == null || tick.getLTP() <= 0) {
                    logger.warn("[JB-EXPIRY] Touchline missing | LegId={} | InstrumentId={}", leg.getId(), leg.getExchangeInstrumentId());
                    continue;
                }

                double executedPrice = state.getExecutedPrice();
                double ltp = tick.getLTP();
                boolean isBuy = LegSide.BUY.getKey().equalsIgnoreCase(leg.getBuySellFlag());
                int qty = (int) Math.abs(leg.getQuantity());

                if (isBuy) {
                    hasHedgeLeg = true;
                }

                // P&L for short: (entry - current) * qty, for long: (current - entry) * qty
                double legPnl = isBuy
                        ? (ltp - executedPrice) * qty
                        : (executedPrice - ltp) * qty;
                totalPnl += legPnl;

            }

            // === Profit Target Exit: Rs 3000 ===
            if (totalPnl >= BASKET_PROFIT_TARGET) {
                logger.info("[JB-EXPIRY] PROFIT TARGET EXIT | PnL={} | StrategyId={}",
                        String.format("%.2f", totalPnl), strategy.getId());
                logJbExpiryExitData(strategy, "Profit Target Reached");
                return true;
            }

            // === Capital Return Exit: 2.5% of deployed capital ===
            if (totalDeployed > 0) {
                double returnPct = (totalPnl / totalDeployed) * 100.0;
                if (returnPct >= CAPITAL_RETURN_PCT) {
                    logger.info("[JB-EXPIRY] CAPITAL RETURN EXIT | Return={}% | PnL={} | StrategyId={}",
                            String.format("%.2f", returnPct), String.format("%.2f", totalPnl), strategy.getId());
                    logJbExpiryExitData(strategy, "Capital Return Threshold Reached");
                    return true;
                }
            }

            // === Supertrend Violation Check (add hedge if needed) ===
            // Only check Supertrend before 3:00 PM (HOLD_CUTOFF_TIME).
            // After 3:00 PM, no new hedge — just hold until basket exit (3:14 PM / profit / capital return).
            if (!hasHedgeLeg && nowIST.isBefore(HOLD_CUTOFF_TIME)) {
                Optional<JbBankniftyExpiryDetectionResult> resultOpt = engineService.getSignal(underlying);
                if (resultOpt.isPresent() && resultOpt.get().isSuperTrendViolated()) {
                    logger.info("[JB-EXPIRY] Supertrend violated → adding hedge | StrategyId={}", strategy.getId());
                    addHedgeLeg(strategy, signal, resultOpt.get());
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("[JB-EXPIRY] Error checking exit | StrategyId={}", strategy.getId(), e);
            return false;
        }
    }

    /**
     * Add protective hedge leg when Supertrend is violated.
     *
     * Step 1: Determine directional bias
     *   - CE premium > PE premium => Bullish => Buy Call protection
     *   - PE premium > CE premium => Bearish => Buy Put protection
     *
     * Step 2: Hedge strike = nearest strike to (ATM Straddle Price + Current Market Price)
     */
    private void addHedgeLeg(Strategy strategy, Signal signal, JbBankniftyExpiryDetectionResult result) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase();

            double cePremium = result.getCePremium();
            double pePremium = result.getPePremium();
            double marketPrice = result.getCurrentMarketPrice();
            double straddlePrice = cePremium + pePremium;

            // Step 1: Directional bias
            boolean buyCall = cePremium > pePremium;  // bullish bias => buy call
            String hedgeType = buyCall ? "CE" : "PE";
            String hedgeCategory = buyCall ? LegType.CALL.getKey() : LegType.PUT.getKey();

            // Step 2: Hedge strike = nearest strike to (straddle price + market price)
            int strikeStep = IndexDifference.fromKey(underlying).getLabel();
            double rawHedgeStrike = straddlePrice + marketPrice;
            int hedgeStrike = (int) (Math.round(rawHedgeStrike / strikeStep) * strikeStep);

            logger.info("[JB-EXPIRY] Adding hedge | Type={} | Strike={} | CePrem={} | PePrem={} | StrategyId={}",
                    hedgeType, hedgeStrike,
                    String.format("%.2f", cePremium), String.format("%.2f", pePremium), strategy.getId());

            String expiryDate = commonUtils.getExpiryShotDateByIndex(
                    strategy.getExpiry(), underlying,
                    com.quantlab.common.utils.staticstore.dropdownutils.OptionType.OPTION.getKey());

            String key = underlying + expiryDate + "-" + hedgeStrike + hedgeType;
            MasterResponseFO master = marketDataFetch.getMasterResponse(key);
            if (master == null) {
                logger.error("[JB-EXPIRY] Master not found for hedge | Key={}", key);
                return;
            }

            MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
            if (data == null) {
                logger.error("[JB-EXPIRY] Touchline not found for hedge | InstrumentId={}", master.getExchangeInstrumentID());
                return;
            }

            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            SignalMapperDto hedge = new SignalMapperDto();
            hedge.setMarketLiveDto(marketLive);
            hedge.setTouchlineBinaryResponse(data);
            hedge.setLegName(key);
            hedge.setMasterData(master);
            hedge.setBuySellFlag(LegSide.BUY.getKey());  // BUY protection
            hedge.setSegment("NSEFO");
            hedge.setCategory(hedgeCategory);
            hedge.setPositionType(strategy.getPositionType());
            hedge.setLegType(LegType.OPEN.getKey());
            hedge.setDerivativeType(
                    com.quantlab.common.utils.staticstore.dropdownutils.OptionType.OPTION.getKey());
            hedge.setLots(1L);
            hedge.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

            // Add hedge leg to existing signal
            Signal updatedSignal = signalService.addLegToSignal(strategy, signal, Collections.singletonList(hedge));

            if (updatedSignal != null && !strategy.getExecutionType()
                    .equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
                grpcService.sendSignal(updatedSignal);
            } else if (updatedSignal == null) {
                logger.warn("[JB-EXPIRY] addLegToSignal returned null | StrategyId={}", strategy.getId());
            }

        } catch (Exception e) {
            logger.error("[JB-EXPIRY] Error adding hedge | StrategyId={}", strategy.getId(), e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            Signal exit = signalService.createExit(strategy);
            if (exit == null) {
                logger.warn("[JB-EXPIRY] createExit returned null | StrategyId={}", strategy.getId());
                return;
            }
            if (!ExecutionTypeMenu.PAPER_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                grpcService.sendExitSignal(exit);
            }
            logger.info("[JB-EXPIRY] Strategy exited | ExitSignalId={} | StrategyId={}", exit.getId(), strategy.getId());
        } catch (Exception ex) {
            logger.error("[JB-EXPIRY] Error exiting strategy | StrategyId={}", strategy.getId(), ex);
            throw new RuntimeException(ex);
        }
    }

    private void logJbExpirySignalData(Strategy strategy, JbBankniftyExpiryDetectionResult result, Signal signal) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("JB-").append(strategy.getUnderlying().getName()).append(" Expiry Signal Created - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Instrument: ").append(strategy.getUnderlying().getName()).append("\n");
            description.append("ATM Strike: ").append(result.getAtmStrike()).append("\n");
            description.append("RSI Value: ").append(String.format("%.2f", result.getRsiValue())).append("\n");
            description.append("Signal Type: Short ATM Straddle (SELL CE + SELL PE)\n");
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
            logger.error("[JB-EXPIRY] Error logging signal data | StrategyId={}", strategy.getId(), e);
        }
    }

    private void logJbExpiryExitData(Strategy strategy, String reason) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("JB-").append(strategy.getUnderlying().getName()).append(" Expiry Exit Triggered - Details:\n");
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
            logger.error("[JB-EXPIRY] Error logging exit data | StrategyId={}", strategy.getId(), e);
        }
    }
}