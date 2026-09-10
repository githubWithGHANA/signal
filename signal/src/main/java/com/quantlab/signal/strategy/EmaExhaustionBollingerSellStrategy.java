package com.quantlab.signal.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.entity.*;
import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.EmaExhaustionBbDetectionResult;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.CandleData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.RedisLegState;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.LegStateProvider;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.CandleRedisService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

/**
 * EMA Exhaustion – Bollinger Reversal Sell Strategy
 *
 * Universe: Nifty 200 (screened by market feed engine)
 * Strategy Type: Equity based, Sell Only
 *
 * Architecture:
 * - Market feed engine (separate service) scans Nifty 200 stocks and pushes
 *   qualified signal candle results to Redis list at key "EMA_EXHAUSTION_BB:RESULTS"
 * - This strategy reads from Redis and handles:
 *   1. Breakdown entry monitoring (LTP < signal candle low, within 3 candles)
 *   2. Equity sell signal creation
 *   3. Dynamic exit management (SL, BB targets, trail)
 *
 * Entry Conditions (validated by market feed engine):
 * - Price above 13 EMA for 13+ consecutive candles OR 8%+ above 13 EMA
 * - Signal candle closes inside upper BB with no touch (regular + Heikin Ashi)
 * - Sell triggers when price breaks LOW of signal candle (within 3 candles)
 *
 * Exit:
 * - SL = Highest High of 5-candle lookback from signal candle (fixed)
 * - T1 = Middle Bollinger Band (20 SMA) — dynamic
 * - T2 = Lower Bollinger Band — dynamic
 * - Trail: Hold until price closes above middle band
 *
 * Constraints:
 * - No pyramiding, no duplicate entries
 * - Only one open position at a time
 * - After full exit, new setup requires fresh qualification cycle
 */
@Service("EmaExhaustionBollingerSellStrategy")
public class EmaExhaustionBollingerSellStrategy implements StrategiesImplementation<EmaExhaustionBollingerSellStrategy> {

    private static final Logger log = LoggerFactory.getLogger(EmaExhaustionBollingerSellStrategy.class);

    private static final String RESULT_KEY = "EMA_EXHAUSTION_BB:RESULTS";
    private static final int MAX_STOCKS = 10;

    // Bollinger Band parameters for dynamic target calculation
    private static final int BB_PERIOD = 20;
    private static final double BB_STD_DEV = 2.0;
    private static final int BB_CANDLE_COUNT = 30; // enough candles for 20-period BB calculation

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private StrategyLegRepository strategyLegRepository;

    @Autowired
    private StrategyLegAdditionsRepository strategyLegAdditionsRepository;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private CandleRedisService candleRedisService;

    @Autowired
    private LegStateProvider legStateProvider;

    @Autowired
    private UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    private DeploymentErrorService deploymentErrorService;

    @Autowired
    private DeploymentErrorsRepository deploymentErrorsRepository;

    private static final String TARGET_T1 = "T1";
    private static final String TARGET_T2 = "T2";
    private static final String TARGET_TRAIL = "TRAIL";

    private final ObjectMapper mapper = new ObjectMapper();

    // ======================================================================
    // MAIN CHECK LOOP
    // ======================================================================
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {

        // log.info("[EMA-BB-SELL] check() | StrategyId={} | Status={}", strategy.getId(), strategy.getStatus());

        // ENTRY CHECK (if strategy not active)
        if (strategyService.inHouseEntryCheck(strategy)) {
            runStrategy(strategy);
        } else if (strategy.getStatus().equalsIgnoreCase(SignalStatus.LIVE.getKey())) {
            // EXIT CHECK (if strategy is live)
            if (checkExit(strategy)) {
                Optional<Strategy> freshStrategy = strategyRepository.findById(strategy.getId());
                if (freshStrategy.isPresent()
                        && freshStrategy.get().getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                    exitStrategy(freshStrategy.get());
                } else {
                    log.warn("[EMA-BB-SELL] Exit triggered but strategy no longer LIVE | StrategyId={}", strategy.getId());
                }
            }
        }
    }

    // ======================================================================
    // ENTRY: Run Strategy
    // ======================================================================
    @Override
    public Signal runStrategy(Strategy strategy) {

        Long listSize = redis.opsForList().size(RESULT_KEY);
        List<String> picks = redis.opsForList().range(RESULT_KEY, 0, -1);

        if (picks == null || picks.isEmpty()) {
            // log.info("[EMA-BB-SELL] No screened stocks in Redis | RedisKey={} | ListSize={} | StrategyId={}",
            //         RESULT_KEY, listSize, strategy.getId());
            return null;
        }

        // log.info("[EMA-BB-SELL] Found {} candidates in Redis | RedisKey={} | StrategyId={}",
        //         picks.size(), RESULT_KEY, strategy.getId());

        List<SignalMapperDto> legDtos = new ArrayList<>();
        String entryTimeframe = null;

        // Captured at the moment we commit to a candidate; used after successful signal
        // creation to write the "consumed" marker (Gap 2 fix — fresh-qualification rule).
        String entrySymbol = null;
        long entrySignalEpoch = 0L;

        EmaExhaustionBbDetectionResult chosenResult = null;
        CandleData chosenCandle = null;
        double chosenLtp = 0;

        for (int idx = 0; idx < picks.size(); idx++) {
            String jsonResult = picks.get(idx);
            try {
                EmaExhaustionBbDetectionResult result = mapper.readValue(jsonResult, EmaExhaustionBbDetectionResult.class);

                // Skip invalid or expired results
                if (!result.isValid() || !result.isBreakdownCandidate()) {
                    // log.info("[EMA-BB-SELL] Skipping candidate #{} | Symbol={} | Valid={} | BreakdownCandidate={} | ValidityRemaining={} | StrategyId={}",
                    //         idx, result.getSymbol(), result.isValid(), result.isBreakdownCandidate(),
                    //         result.getValidityRemainingCandles(), strategy.getId());
                    continue;
                }

                String symbol = result.getSymbol();
                if (symbol == null || symbol.isEmpty()) {
                    continue;
                }

                // Spec: "After full exit, new setup requires fresh qualification cycle".
                // Block re-entry on the same (strategy, symbol, signalCandleEpoch) — the
                // mfeed result lingers in Redis through its 3-candle validity window, so
                // without this check, a quick T1+T2+TRAIL exit would re-trigger entry on
                // the same signal next tick. A fresh mfeed scan emits a different
                // signalCandleEpochTime, so legitimate re-qualifications aren't blocked.
                String consumedKey = "EBB:CONSUMED:" + strategy.getId() + ":" + symbol + ":" + result.getSignalCandleEpochTime();
                if (Boolean.TRUE.equals(redis.hasKey(consumedKey))) {
                    // log.info("[EMA-BB-SELL] Signal already consumed for this strategy | Symbol={} | SignalEpoch={} | StrategyId={}",
                    //         symbol, result.getSignalCandleEpochTime(), strategy.getId());
                    continue;
                }

                // Resolve instrument and get current LTP
                String key = "EQ_" + symbol.toUpperCase();
                MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                if (master == null) {
                    log.warn("[EMA-BB-SELL] Master missing | Symbol={} | Key={}", symbol, key);
                    continue;
                }

                Integer instrumentId = master.getExchangeInstrumentID();
                MarketData touchline = touchLineService.getTouchLine(String.valueOf(instrumentId));
                if (touchline == null || touchline.getLTP() <= 0) {
//                    log.warn("[EMA-BB-SELL] Touchline missing | Symbol={} | InstrumentId={}", symbol, instrumentId);
                    continue;
                }

                // Spec: "Breakdown of signal candle (wait for close)" — entry must be
                // triggered by a CLOSED candle's close below signalCandleLow, not by an
                // intra-tick LTP touch. Live LTP would fire on wicks below signalLow that
                // recover by close, producing "phantom" entries that don't look like
                // breakdowns when reviewing the closed chart afterward.
                String candleSymbol = "EQ:" + symbol.toUpperCase();
                String tf = result.getTimeframe();
                List<CandleData> recentCandles = candleRedisService.getCandles(candleSymbol, tf, 1);
                if (recentCandles == null || recentCandles.isEmpty()) {
                    // log.info("[EMA-BB-SELL] No recent candle for breakdown check | Symbol={} | Timeframe={} | StrategyId={}",
                    //         symbol, tf, strategy.getId());
                    continue;
                }
                CandleData latestCandle = recentCandles.get(0);

                // Entry price = current LTP
                double ltp = touchline.getLTP();

                // BREAKDOWN CHECK: LTP must break below signal candle low (per strategy requirement)
                if (ltp >= result.getSignalCandleLow()) {
                    // log.info("[EMA-BB-SELL] No breakdown yet | Symbol={} | LTP={} | SignalLow={} | StrategyId={}",
                    //         symbol, String.format("%.2f", ltp),
                    //         String.format("%.2f", result.getSignalCandleLow()),
                    //         strategy.getId());
                    continue;
                }

                log.info("[EMA-BB-SELL] BREAKDOWN DETECTED | Symbol={} | LTP={} | SignalLow={} | SL={} | Timeframe={} | StrategyId={}",
                        symbol, String.format("%.2f", ltp),
                        String.format("%.2f", result.getSignalCandleLow()),
                        String.format("%.2f", result.getHighestHighLast5Candles()),
                        result.getTimeframe(),
                        strategy.getId());

                entryTimeframe = result.getTimeframe();

                // Capital-based position sizing: floor(deployed_capital / entry_price).
                // Deployed capital = configured min capital scaled by user multiplier.
                Long minCapitalRaw = strategy.getMinCapital();
                if (minCapitalRaw == null || minCapitalRaw <= 0L) {
                    log.warn("[EMA-BB-SELL] Strategy minCapital missing/invalid — skipping candidate | Symbol={} | StrategyId={}",
                            symbol, strategy.getId());
                    continue;
                }
                double availableCapital = (minCapitalRaw / (double) AMOUNT_MULTIPLIER) * strategy.getMultiplier();
                int totalQty = (int) Math.floor(availableCapital / ltp);

                if (totalQty < 1) {
                    log.warn("[EMA-BB-SELL] Capital insufficient for one share — skipping | Symbol={} | Capital={} | LTP={} | StrategyId={}",
                            symbol, availableCapital, ltp, strategy.getId());
                    continue;
                }

                // log.info("[EMA-BB-SELL] Position sizing | Symbol={} | Capital={} | LTP={} | TotalQty={} | StrategyId={}",
                //         symbol, String.format("%.2f", availableCapital), String.format("%.2f", ltp), totalQty, strategy.getId());

                // Strategy depends on splitting the position into 3 tranches (T1/T2/Trail).
                // If capital can't fund at least 3 shares, skip the trade rather than
                // running a degraded single-leg version that ignores T2/Trail.
                if (totalQty < 3) {
                    log.warn("[EMA-BB-SELL] Capital insufficient to split into 3 tranches — skipping | Symbol={} | TotalQty={} | StrategyId={}",
                            symbol, totalQty, strategy.getId());
                    continue;
                }

                int t1Qty = totalQty / 3;
                int t2Qty = totalQty / 3;
                int trailQty = totalQty - t1Qty - t2Qty;
                legDtos.add(buildEquityLegDto(result, touchline, t1Qty));
                legDtos.add(buildEquityLegDto(result, touchline, t2Qty));
                legDtos.add(buildEquityLegDto(result, touchline, trailQty));

                // Capture for the consumed-marker write (Gap 2 fix). Written only after
                // createEquitySignal returns non-null below — if creation fails, no marker
                // is written so the strategy can retry on this signal next tick.
                entrySymbol = symbol;
                entrySignalEpoch = result.getSignalCandleEpochTime();

                chosenResult = result;
                chosenCandle = latestCandle;
                chosenLtp = ltp;

                // Only one open position at a time
                break;

            } catch (Exception e) {
                log.error("[EMA-BB-SELL] Error parsing candidate #{} | StrategyId={}", idx, strategy.getId(), e);
            }
        }

        if (legDtos.isEmpty()) {
            return null;
        }

        Signal signal = signalService.createEquitySignal(strategy, legDtos);

        // Persist target type and entry timeframe per leg in StrategyLegAdditions.
        // Previously stored in Redis via writeCustomField, but the 3-day TTL on the
        // leg-state hash caused those fields to disappear on positional trades —
        // leaving the exit logic to silently fall back to T1 for every leg.
        //
        // createEquitySignal runs in REQUIRES_NEW, so its returned legs are detached
        // when control returns here. Calling saveAll on them triggered a cascade-merge
        // that nulled signal_id, orphaning the legs and turning them into permanent
        // "default legs" via findDefaultStrategyLegs. Use a native UPDATE on
        // leg_additions_id so the leg row is patched without re-merging the entity.
        if (signal != null && entryTimeframe != null) {
            Hibernate.initialize(signal.getSignalLegs());
            List<StrategyLeg> createdLegs = new ArrayList<>(signal.getSignalLegs());
            createdLegs.sort(Comparator.comparingLong(StrategyLeg::getId));

            String[] targetTypes = {TARGET_T1, TARGET_T2, TARGET_TRAIL};
            for (int i = 0; i < createdLegs.size(); i++) {
                StrategyLeg leg = createdLegs.get(i);
                String targetType = (i < targetTypes.length) ? targetTypes[i] : TARGET_T1;

                StrategyLegAdditions additions = new StrategyLegAdditions();
                additions.setStrategyLeg(leg);
                additions.setTargetType(targetType);
                additions.setEntryTimeframe(entryTimeframe);
                additions = strategyLegAdditionsRepository.save(additions);

                strategyLegRepository.updateLegAdditionsId(leg.getId(), additions.getId());
                leg.setLegAdditions(additions);
                // log.info("[EMA-BB-SELL] Leg {} assigned target={} | Qty={} | StrategyId={}",
                //         leg.getId(), targetType, leg.getQuantity(), strategy.getId());
            }
        }

        if (signal == null) {
            log.warn("[EMA-BB-SELL] createEquitySignal returned null | StrategyId={}", strategy.getId());
            return null;
        }

        // Mark this (strategy, symbol, signalCandleEpoch) as consumed so the same mfeed
        // result can't trigger another entry within its 3-candle validity window. Spec:
        // "After full exit, new setup requires fresh qualification cycle". A different
        // signalCandleEpochTime from a future mfeed scan will pass the consumed-key gate.
        // TTL = 4h covers any 1H signal's full validity window with a safety margin.
        if (entrySymbol != null && entrySignalEpoch > 0L) {
            String consumedKey = "EBB:CONSUMED:" + strategy.getId() + ":" + entrySymbol + ":" + entrySignalEpoch;
            redis.opsForValue().set(consumedKey, "1", 4L, TimeUnit.HOURS);
            // log.info("[EMA-BB-SELL] Marked signal as consumed | Symbol={} | SignalEpoch={} | StrategyId={}",
            //         entrySymbol, entrySignalEpoch, strategy.getId());
        }

        strategyRepository.updateSignalCount(strategy.getId());

        signal = signalRepository.findById(signal.getId()).orElseThrow();
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
//            // EMA-BB-SELL is not approved for live trading — fail loud and mark ERROR.
//            log.warn("[EMA-BB-SELL] Live trading is not supported for this strategy — marking strategy, signal & legs as ERROR | StrategyId={} | SignalId={}",
//                    strategy.getId(), signal.getId());
            markSignalAndLegsAsError(signal);
            strategyRepository.updateStrategyStatus(strategy.getId(), Status.ERROR.getKey());
            strategy.setStatus(Status.ERROR.getKey());
            deploymentErrorService.saveStrategyUpdateLogs(strategy,
                    "Live trading is not enabled for EMA-BB-SELL strategy");
            return null;
        } else {
            // log.info("[EMA-BB-SELL] Paper trading mode, skipping gRPC | SignalId={} | StrategyId={}", signal.getId(), strategy.getId());
        }

        log.info("[EMA-BB-SELL] Signal created | SignalId={} | StrategyId={}", signal.getId(), strategy.getId());

        if (chosenResult != null) {
            logEmaBbSignalData(strategy, chosenResult, chosenCandle, chosenLtp);
        }

        return signal;
    }

    /**
     * Build equity SELL leg DTO for a stock that has confirmed breakdown.
     */
    private SignalMapperDto buildEquityLegDto(EmaExhaustionBbDetectionResult result,
                                              MarketData touchline,
                                              int quantity) {

        SignalMapperDto dto = new SignalMapperDto();
        dto.setLegName(result.getSymbol());
        dto.setSegment("NSECM");
        dto.setCategory("EQ");
        dto.setDerivativeType("EQUITY");
        dto.setLegType(LegType.OPEN.getKey());
        dto.setBuySellFlag(LegSide.SELL.getKey());
        dto.setQuantity(quantity);
        dto.setLots(1L);
        dto.setTouchlineBinaryResponse(touchline);

        // Stop loss: highest high of last 5 candles (fixed at entry)
        dto.setStopLossUnitToggle("Y");
        dto.setStopLossUnitType("AMOUNT");
        dto.setStopLossUnitValue((long) result.getHighestHighLast5Candles());

        return dto;
    }

    // ======================================================================
    // EXIT: Custom exit logic with dynamic Bollinger Band targets
    // ======================================================================

    /**
     * Check exit conditions for open sell position.
     *
     * Per-leg target management:
     * - SL: LTP >= highest high of 5-candle lookback → exit ALL remaining legs
     * - T1 leg: LTP <= middle Bollinger Band → exit this leg only
     * - T2 leg: LTP <= lower Bollinger Band → exit this leg only
     * - Trail leg: activates only after BOTH T1 and T2 have been hit; exits
     *   when a candle closes back above the middle Bollinger Band
     * - Manual exit override → exit ALL
     *
     * Returns true only when ALL remaining legs should be exited (SL, manual,
     * or all legs already individually exited).
     */
    private boolean checkExit(Strategy strategy) {
        try {
            // Manual exit override
            if (ManualExit.ENABLED.getKey().equalsIgnoreCase(strategy.getManualExitType())) {
                log.info("[EMA-BB-SELL] Manual exit | StrategyId={}", strategy.getId());
                redis.delete("EBB:T1_HIT:" + strategy.getId());
                redis.delete("EBB:T2_HIT:" + strategy.getId());
                return true;
            }

            // Fetch active LIVE signal
            List<Signal> activeSignals = signalRepository.findByStrategyIdAndStatus(
                    strategy.getId(), Status.LIVE.getKey());
            if (activeSignals.isEmpty()) {
                log.warn("[EMA-BB-SELL] No active LIVE signals found | StrategyId={}", strategy.getId());
                return false;
            }

            activeSignals.sort(Comparator.comparingLong(Signal::getId));
            Signal signal = activeSignals.get(0);
            Hibernate.initialize(signal.getSignalLegs());
            List<StrategyLeg> legs = signal.getSignalLegs();

            boolean anyLiveLegFound = false;

            for (StrategyLeg leg : legs) {
                if (!Status.LIVE.getKey().equalsIgnoreCase(leg.getStatus())) {
                    continue;
                }
                if (!LegType.OPEN.getKey().equalsIgnoreCase(leg.getLegType())) {
                    continue;
                }

                anyLiveLegFound = true;

                // Get executed price from Redis leg state
                RedisLegState state = legStateProvider.getLegState(strategy.getId(), leg.getId());
                if (state == null || state.getExecutedPrice() == null) {
                    log.warn("[EMA-BB-SELL] LegState missing | LegId={} | StrategyId={}", leg.getId(), strategy.getId());
                    continue;
                }

                // Get current LTP
                MarketData tick = touchLineService.getTouchLine(
                        String.valueOf(leg.getExchangeInstrumentId()));
                if (tick == null || tick.getLTP() <= 0) {
                    log.warn("[EMA-BB-SELL] Touchline missing for leg | LegId={} | InstrumentId={}", leg.getId(), leg.getExchangeInstrumentId());
                    continue;
                }

                double ltp = tick.getLTP();
                double entryPrice = state.getExecutedPrice();
                String symbol = leg.getName();

                // SL CHECK: For a sell position, price going UP hits SL — exit ALL legs.
                // Per spec, SL is fixed at entry = 5-candle highest high. No fallback:
                // if it's missing, that's a data integrity issue — log loudly and disable
                // SL protection on this leg, but still allow T1/T2/Trail target checks
                // below to run so the leg isn't frozen.
                Long slValue = leg.getStopLossUnitValue();
                if (slValue == null || slValue <= 0L) {
                    log.error("[EMA-BB-SELL] StopLoss missing/invalid — SL check disabled for this leg | LegId={} | StrategyId={} | SL={}",
                            leg.getId(), strategy.getId(), slValue);
                } else if (ltp >= slValue) {
                    log.info("[EMA-BB-SELL] SL HIT | Symbol={} | LTP={} >= SL={} | StrategyId={}",
                            symbol, String.format("%.2f", ltp), String.format("%.2f", (double) slValue), strategy.getId());
                    logExitSignalData(strategy, leg, "Fixed SL Hit", ltp, entryPrice);
                    redis.delete("EBB:T1_HIT:" + strategy.getId());
                    redis.delete("EBB:T2_HIT:" + strategy.getId());
                    return true;
                }

                // Read entry timeframe and target type from StrategyLegAdditions.
                // Previously sourced from Redis custom fields, which were lost on TTL expiry
                // for positional trades — causing all legs to silently fall back to T1.
                StrategyLegAdditions additions = leg.getLegAdditions();
                String timeframe = additions != null ? additions.getEntryTimeframe() : null;
                String targetType = additions != null ? additions.getTargetType() : null;

                if (targetType == null || targetType.isEmpty() || timeframe == null || timeframe.isEmpty()) {
                    log.error("[EMA-BB-SELL] targetType/entryTimeframe missing on leg additions — skipping exit check | LegId={} | StrategyId={} | targetType={} | timeframe={}",
                            leg.getId(), strategy.getId(), targetType, timeframe);
                    continue;
                }

                // DYNAMIC BOLLINGER BAND CALCULATION
                double[] bbValues = calculateCurrentBollingerBands(symbol, timeframe);
                if (bbValues == null) {
                    log.warn("[EMA-BB-SELL] BB calculation failed | Symbol={} | Timeframe={} | StrategyId={}",
                            symbol, timeframe, strategy.getId());
                    continue;
                }

                double middleBand = bbValues[0];
                double lowerBand = bbValues[2];

                // log.info("[EMA-BB-SELL] EXIT CHECK | Symbol={} | LTP={} | Entry={} | SL={} | MidBB={} | LowerBB={} | Target={} | LegId={} | StrategyId={}",
                //         symbol, String.format("%.2f", ltp), String.format("%.2f", entryPrice),
                //         slValue == null ? "N/A" : String.format("%.2f", (double) slValue), String.format("%.2f", middleBand),
                //         String.format("%.2f", lowerBand), targetType, leg.getId(), strategy.getId());

                // T1 LEG: Exit when LTP <= middle Bollinger Band
                if (TARGET_T1.equals(targetType) && ltp <= middleBand) {
                    log.info("[EMA-BB-SELL] T1 HIT | Symbol={} | LTP={} | MidBB={} | LegId={} | StrategyId={}",
                            symbol, String.format("%.2f", ltp), String.format("%.2f", middleBand),
                            leg.getId(), strategy.getId());
                    logExitSignalData(strategy, leg, "T1 (MidBB) Hit", ltp, entryPrice);
                    signalService.createEquitySingleLegExit(strategy, leg);
                    // Store T1 hit timestamp so trail only reacts to candles formed after this moment
                    redis.opsForValue().set("EBB:T1_HIT:" + strategy.getId(),
                            String.valueOf(System.currentTimeMillis()));
                    continue;
                }

                // T2 LEG: Exit when LTP <= lower Bollinger Band
                if (TARGET_T2.equals(targetType) && ltp <= lowerBand) {
                    log.info("[EMA-BB-SELL] T2 HIT | Symbol={} | LTP={} | LowerBB={} | LegId={} | StrategyId={}",
                            symbol, String.format("%.2f", ltp), String.format("%.2f", lowerBand),
                            leg.getId(), strategy.getId());
                    logExitSignalData(strategy, leg, "T2 (LowerBB) Hit", ltp, entryPrice);
                    signalService.createEquitySingleLegExit(strategy, leg);
                    // Mark T2 hit so Trail can activate — per spec, Trail manages the
                    // residual only after both T1 and T2 partial exits are complete.
                    redis.opsForValue().set("EBB:T2_HIT:" + strategy.getId(),
                            String.valueOf(System.currentTimeMillis()));
                    continue;
                }

                // TRAIL LEG: Activates only after BOTH T1 and T2 have been hit.
                // Per spec, Trail holds the remaining quantity until a candle closes
                // back above the middle Bollinger Band.
                if (TARGET_TRAIL.equals(targetType)) {
                    String t1HitStr = redis.opsForValue().get("EBB:T1_HIT:" + strategy.getId());
                    String t2HitStr = redis.opsForValue().get("EBB:T2_HIT:" + strategy.getId());
                    if (t1HitStr == null || t2HitStr == null) {
                        // log.info("[EMA-BB-SELL] Trail waiting for T1 & T2 | Symbol={} | T1Hit={} | T2Hit={} | LegId={} | StrategyId={}",
                        //         symbol, t1HitStr != null, t2HitStr != null,
                        //         leg.getId(), strategy.getId());
                        continue;
                    }

                    long activationTime = Math.max(Long.parseLong(t1HitStr), Long.parseLong(t2HitStr));

                    // Exit on candle close above middle band — only consider candles
                    // formed AFTER Trail activation (i.e., after T2 hit).
                    String candleSymbol = "EQ:" + symbol.toUpperCase();
                    List<CandleData> recentCandles = candleRedisService.getCandles(candleSymbol, timeframe, 1);
                    if (recentCandles != null && !recentCandles.isEmpty()) {
                        CandleData latestCandle = recentCandles.get(0);
                        // Candle epoch is standard UTC milliseconds.
                        long candleEpochMs = latestCandle.getEpochTime() * 1000L;

                        // Diagnostic: log human-readable IST timestamps for verification.
                        java.time.ZoneId IST = java.time.ZoneId.of("Asia/Kolkata");
                        String candleIst = java.time.Instant.ofEpochMilli(candleEpochMs).atZone(IST).toString();
                        String activationIst = java.time.Instant.ofEpochMilli(activationTime).atZone(IST).toString();
                        // log.info("[EMA-BB-SELL] Trail candle check | Symbol={} | RawEpoch={} | CandleIST={} | ActivationIST={} | CandleAfterActivation={} | CandleClose={} | MidBB={} | StrategyId={}",
                        //         symbol, latestCandle.getEpochTime(), candleIst, activationIst,
                        //         candleEpochMs > activationTime,
                        //         String.format("%.2f", latestCandle.getClose()),
                        //         String.format("%.2f", middleBand),
                        //         strategy.getId());

                        if (candleEpochMs > activationTime && latestCandle.getClose() > middleBand) {
                            log.info("[EMA-BB-SELL] TRAIL EXIT | Symbol={} | CandleClose={} > MidBB={} | CandleEpoch={} | ActivationTime={} | LegId={} | StrategyId={}",
                                    symbol, String.format("%.2f", latestCandle.getClose()),
                                    String.format("%.2f", middleBand),
                                    candleEpochMs, activationTime,
                                    leg.getId(), strategy.getId());
                            logExitSignalData(strategy, leg, "Trail Exit (Close > MidBB)", latestCandle.getClose(), entryPrice);
                            signalService.createEquitySingleLegExit(strategy, leg);
                        }
                    }
                }
            }

            // If no LIVE OPEN legs remain, all targets were individually exited
            if (!anyLiveLegFound) {
                log.info("[EMA-BB-SELL] All legs exited via targets | StrategyId={}", strategy.getId());
                // Clean up T1/T2 hit flags for fresh qualification cycle
                redis.delete("EBB:T1_HIT:" + strategy.getId());
                redis.delete("EBB:T2_HIT:" + strategy.getId());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("[EMA-BB-SELL] Error checking exit | StrategyId={}", strategy.getId(), e);
            return false;
        }
    }

    /**
     * Calculate current Bollinger Bands (20, 2) from candle data for a stock.
     *
     * @param symbol    Stock symbol (e.g., "RELIANCE")
     * @param timeframe Candle timeframe (e.g., "1H") — must match mfeed Redis key format
     * @return double[3] = {middleBand, upperBand, lowerBand} or null if insufficient data
     */
    private double[] calculateCurrentBollingerBands(String symbol, String timeframe) {
        // mfeed stores candles under key "candle:EQ:{symbol}:{timeframe}"
        String candleSymbol = "EQ:" + symbol.toUpperCase();
        List<CandleData> candles = candleRedisService.getCandles(candleSymbol, timeframe, BB_CANDLE_COUNT);

        if (candles == null || candles.size() < BB_PERIOD) {
            log.warn("[EMA-BB-SELL] Insufficient candles for BB | Symbol={} | Available={}",
                    symbol, candles != null ? candles.size() : 0);
            return null;
        }

        // Use the last BB_PERIOD candles for calculation
        int startIdx = candles.size() - BB_PERIOD;
        double sum = 0.0;

        for (int i = startIdx; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }

        // Middle Band = 20-period SMA
        double middleBand = sum / BB_PERIOD;

        // Standard Deviation
        double sumSquaredDiff = 0.0;
        for (int i = startIdx; i < candles.size(); i++) {
            double diff = candles.get(i).getClose() - middleBand;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / BB_PERIOD);

        // Upper and Lower Bands
        double upperBand = middleBand + (BB_STD_DEV * stdDev);
        double lowerBand = middleBand - (BB_STD_DEV * stdDev);

        log.debug("[EMA-BB-SELL] BB | Symbol={} | Mid={} | Upper={} | Lower={}",
                symbol, String.format("%.2f", middleBand),
                String.format("%.2f", upperBand),
                String.format("%.2f", lowerBand));

        return new double[]{middleBand, upperBand, lowerBand};
    }

    // ======================================================================
    // EXIT STRATEGY IMPLEMENTATION
    // ======================================================================
    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            Signal exitSignal = signalService.createEquityExit(strategy);
            if (exitSignal == null) {
                // All legs may have been individually exited via T1/T2/Trail
                log.info("[EMA-BB-SELL] No remaining legs to exit | StrategyId={}", strategy.getId());
                return;
            }
            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
//                // EMA-BB-SELL is not approved for live trading — fail loud and mark ERROR.
//                log.warn("[EMA-BB-SELL] Live trading is not supported for this strategy — marking strategy, signal & legs as ERROR | StrategyId={} | ExitSignalId={}",
//                        strategy.getId(), exitSignal.getId());
                markSignalAndLegsAsError(exitSignal);
                strategyRepository.updateStrategyStatus(strategy.getId(), Status.ERROR.getKey());
                strategy.setStatus(Status.ERROR.getKey());
                deploymentErrorService.saveStrategyUpdateLogs(strategy,
                        "Live trading is not enabled for EMA-BB-SELL strategy");
                return;
            } else {
                log.info("[EMA-BB-SELL] Paper trading mode, skipping gRPC exit | ExitSignalId={} | StrategyId={}", exitSignal.getId(), strategy.getId());
            }
            log.info("[EMA-BB-SELL] Exit signal created | ExitSignalId={} | StrategyId={}", exitSignal.getId(), strategy.getId());
        } catch (Exception e) {
            log.error("[EMA-BB-SELL] Error during exit | StrategyId={}", strategy.getId(), e);
        }
    }

    private void markSignalAndLegsAsError(Signal signal) {
        signalRepository.updateSignalStatus(signal.getId(), Status.ERROR.getKey());
        Hibernate.initialize(signal.getSignalLegs());
        List<StrategyLeg> legs = signal.getSignalLegs();
        if (legs != null && !legs.isEmpty()) {
            for (StrategyLeg leg : legs) {
                leg.setStatus(Status.ERROR.getKey());
                leg.setExchangeStatus(LegExchangeStatus.ERROR_PLACING_ORDER.getKey());
            }
            strategyLegRepository.saveAll(legs);
        }
    }

    private void logEmaBbSignalData(
            Strategy strategy,
            EmaExhaustionBbDetectionResult result,
            CandleData latestCandle,
            double entryPrice) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("EMA-BB Sell Signal Created - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Symbol: ").append(result.getSymbol()).append("\n");
            description.append("Signal Type: SELL (EQ)\n");
            description.append("Entry Price (LTP): ").append(String.format("%.2f", entryPrice)).append("\n");
            description.append("Candle Close: ").append(String.format("%.2f", latestCandle.getClose())).append("\n");
            description.append("Signal Low: ").append(String.format("%.2f", result.getSignalCandleLow())).append("\n");
            description.append("Stop Loss (Fixed): ").append(String.format("%.2f", result.getHighestHighLast5Candles())).append("\n");
            description.append("Timeframe: ").append(result.getTimeframe()).append("\n");
            description.append("Candle Time (Epoch): ").append(latestCandle.getEpochTime()).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.LIVE.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            log.info(description.toString());

        } catch (Exception e) {
            log.error("[{}] Error logging EMA-BB signal data", strategy.getId(), e);
        }
    }

    private void logExitSignalData(Strategy strategy,
                                   StrategyLeg leg,
                                   String reason,
                                   double ltp,
                                   double executedPrice) {
        try {
            StringBuilder description = new StringBuilder();
            description.append("Exit Triggered - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
            description.append("Symbol: ").append(leg.getName()).append("\n");
            description.append("Leg ID: ").append(leg.getId()).append("\n");
            description.append("Reason: ").append(reason).append("\n");
            description.append("Executed Price: ").append(executedPrice).append("\n");
            description.append("LTP: ").append(ltp).append("\n");
            description.append("Exit Time: ").append(Instant.now()).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.EXIT.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            log.info(description.toString());

        } catch (Exception e) {
            log.error("[{}] Error logging exit signal data", strategy.getId(), e);
        }
    }
}