package com.quantlab.signal.strategy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.MinimalLegDto;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.CandleData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.RedisLegState;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.LegStateProvider;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.CandleRedisService;
import com.quantlab.signal.service.redisService.RedisLegService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.Hibernate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.common.utils.staticstore.AppConstants.TOGGLE_TRUE;

@Service("AtmOptionAccumulationStrategy")
public class AtmOptionAccumulationStrategy implements StrategiesImplementation<AtmOptionAccumulationStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(AtmOptionAccumulationStrategy.class);

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    private static final LocalTime ENTRY_BLOCK_START = LocalTime.of(11, 0);
    private static final LocalTime ENTRY_BLOCK_END   = LocalTime.of(13, 0);


    private static final double PRICE_THRESHOLD = 0.20; // 0.20% price move
    private static final double VOLUME_MULTIPLIER_5M = 2.5;
    private static final double VOLUME_MULTIPLIER_15M = 2.0;
    private static final double VOLUME_MULTIPLIER_1H = 1.9;
    private static final double STOPLOSS_PCT = 15.0;
    private static final double TARGET_PCT = 10.0;
    // Nifty defaults (Sensex overrides via UnderlyingParams)
    private static final double ABSOLUTE_STOP_LOSS = 15.0;
    private static final Long TRAILING_DISTANCE = 5L;
    private static final Duration CANDLE_TIMESTAMP_TOLERANCE = Duration.ofSeconds(30);
    private static final Duration CANDLE_DEBOUNCE_DELAY = Duration.ofSeconds(10);

    private static final int MIN_CANDLES_FOR_VWAP = 10;
    private static final double VWAP_MARGIN = 0.0005; // optional 0.05% buffer


    // Configurable strike step and depth. Depth=1 => ITM/ATM/OTM
    private static final int ITM_DEPTH = 2;
    private static final int STRIKE_STEP = 50;
    private static final int STRIKE_DEPTH = 1;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private DeploymentErrorsRepository deploymentErrorsRepository;

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
    private CandleRedisService candleRedisService;

    @Autowired
    private StrategyLegRepository strategyLegRepository;

    @Autowired
    private LegStateProvider legStateProvider;

    @Autowired
    private RedisLegService redisLegService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StrategyAdditionsRepository strategyAdditionsRepository;

    private final LoadingCache<String, MasterResponseFO> masterCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(5, TimeUnit.SECONDS)
                    .build(new CacheLoader<String, MasterResponseFO>() {
                        @Override
                        public MasterResponseFO load(String key) throws Exception {
                            return marketDataFetch.getMasterResponse(key);
                        }
                    });

    private final LoadingCache<String, List<CandleData>> candleCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(400, TimeUnit.MILLISECONDS)
                    .maximumSize(20_000)                            // prevents memory bloat
                    .build(new CacheLoader<>() {
                        @Override
                        public List<CandleData> load(String key) throws Exception {
                            return fetchCandlesFromKey(key);
                        }
                    });

    private List<CandleData> fetchCandlesFromKey(String key) {
        try {
            // key expected: underlying:instrumentId:timeframe:count
            String[] parts = key.split(":");
            if (parts.length != 4) {
                logger.error("Invalid candle cache key: {}", key);
                return List.of();
            }

            String underlying = parts[0];
            String instrumentId = parts[1];
            String timeframe = parts[2];
            int count = Integer.parseInt(parts[3]);

            // Construct Redis key exactly as you do today
            String redisKey = underlying + ":" + instrumentId;

            return candleRedisService.getCandles(redisKey, timeframe, count);

        } catch (Exception ex) {
            logger.error("Error parsing candle cache key={} : {}", key, ex.getMessage());
            return List.of();
        }
    }


    private List<CandleData> getCachedCandles(String underlying, String instrumentId, String timeframe, int count) {
        String cacheKey = underlying + ":" + instrumentId + ":" + timeframe + ":" + count;

        try {
            return candleCache.get(cacheKey);
        } catch (ExecutionException e) {
            logger.error("Candle cache load failed for key={} : {}", cacheKey, e.getMessage());
            return candleRedisService.getCandles(underlying + ":" + instrumentId, timeframe, count);
        }
    }


    @Override
    public Signal runStrategy(Strategy strategy) {
        try {
            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            IndexInstruments instrument = IndexInstruments.fromKey(underlying);
            String instrumentId = instrument.getLabel().toString();

            // === Step 1: Fetch Spot and Compute Synthetic ATM ===
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            double spot = marketLive.getSpotPrice();

            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, underlying);
            int atmStrike = marketDataFetch.getATM(underlying, (int) syntheticPrice , commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()));

            UnderlyingParams params = UnderlyingParams.resolve(underlying);

//            logger.info("[{}] Synthetic ATM Selection | SpotATM={} | Synthetic={} | Selected={}",
//                    underlying, spot, syntheticPrice, atmStrike);

            // === Step 2: Prepare list of strikes to check (ITM, ATM, OTM by default) ===
            List<Integer> strikesToCheck = IntStream.rangeClosed(-STRIKE_DEPTH, STRIKE_DEPTH)
                    .map(i -> atmStrike + i * params.strikeStep)
                    .boxed()
                    .collect(Collectors.toList());

            // === Step 3: Scan strikes for accumulation signals ===
            Optional<AccumSignalChoice> chosen = detectAccumulationAcrossStrikes(underlying, strikesToCheck, strategy);

            if (chosen.isEmpty()) {
//                logger.info("[{}] No accumulation signal detected across strikes: {}", underlying, strikesToCheck);
                return null;
            }

            AccumSignalChoice chosenSignal = chosen.get();
            int selectedStrike = chosenSignal.getStrike();
            AccumSignal sig = chosenSignal.getSignal();

            String legType = sig.isCall() ? SegmentType.CE.getKey() : SegmentType.PE.getKey();
            LegType legEnum = sig.isCall() ? LegType.CALL : LegType.PUT;

            int rawDeepItmStrike = sig.isCall()
                    ? atmStrike - (ITM_DEPTH * params.strikeStep)
                    : atmStrike + (ITM_DEPTH * params.strikeStep);

            // Round to nearest 100 for liquidity
            int deepItmStrike;

            if (sig.isCall()) {
                // Calls: deeper ITM = lower strikes → floor to nearest 100
                deepItmStrike = (rawDeepItmStrike / 100) * 100;
            } else {
                // Puts: deeper ITM = higher strikes → ceil to nearest 100
                deepItmStrike = ((rawDeepItmStrike + 99) / 100) * 100;
            }

            // For safety
            if (sig.isCall() && deepItmStrike > rawDeepItmStrike) {
                deepItmStrike -= 100;
            } else if (!sig.isCall() && deepItmStrike < rawDeepItmStrike) {
                deepItmStrike += 100;
            }


            String optionKey = commonUtils.buildOptionSymbol(underlying, strategy.getExpiry(), deepItmStrike, legType);

            // === Step 4: Prepare trade details ===
            MasterResponseFO optionMaster = marketDataFetch.getMasterResponse(optionKey);
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(optionMaster.getExchangeInstrumentID()));

            SignalMapperDto leg = new SignalMapperDto();
            leg.setMarketLiveDto(marketLive);
            leg.setTouchlineBinaryResponse(touchline);
            leg.setMasterData(optionMaster);
            leg.setLegName(optionKey);
            leg.setBuySellFlag(LegSide.BUY.getKey());
            leg.setSegment("NSEFO");
            leg.setCategory(legEnum.getKey());
            leg.setOptionType(legType);
            leg.setDerivativeType(OptionType.OPTION.getKey());
            leg.setPositionType(strategy.getPositionType());
            leg.setLots(1L);
            leg.setLegType(LegType.OPEN.getKey());
            leg.setQuantity((int) (optionMaster.getLotSize() * strategy.getMultiplier()));

            double entryPrice = touchline.getLTP();
            leg.setTargetUnitToggle(TOGGLE_TRUE);
            leg.setTargetUnitType("PERCENT");
            leg.setTargetUnitValue((long) (TARGET_PCT * AMOUNT_MULTIPLIER));     // store 10.0%
            leg.setStopLossUnitToggle(TOGGLE_TRUE);
            leg.setStopLossUnitType("PERCENT");
            leg.setStopLossUnitValue((long) (STOPLOSS_PCT * AMOUNT_MULTIPLIER)); // store 15.0%

            // === Step 5: Create & Send Signal ===
            Signal createdSignal = signalService.createSignal(strategy, Collections.singletonList(leg));
            strategyRepository.updateSignalCount(strategy.getId());

            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
                grpcService.sendSignal(createdSignal);
                logger.info("[{}] Sent Accumulation Signal | Strike={} | Type={} | TF={} | VolMult={} | ScannedStrikes={}",
                        underlying, deepItmStrike, legType, sig.getTimeframe(), sig.getVolumeMultiplier(), strikesToCheck);
            }

            return createdSignal;

        } catch (Exception e) {
            logger.error("Error running ATM Option Accumulation Strategy for strategyId={}", strategy.getId(), e);
            signalService.errorCreatingSignal(strategy, e);
            return null;
        }
    }

    /**
     * Scans multiple strikes (ITM/ATM/OTM) for accumulation signals and returns the chosen signal
     * with strike according to priority rules:
     *   1) If ATM has any valid signal -> choose ATM (even if lower priority TF)
     *   2) Otherwise choose the highest priority AccumSignal across strikes (1h>15m>5m)
     */
    private Optional<AccumSignalChoice> detectAccumulationAcrossStrikes(String underlying, List<Integer> strikesToCheck, Strategy strategy) {
        List<AccumSignalChoice> allSignals = new ArrayList<>();

        for (Integer strike : strikesToCheck) {
            try {
                // Resolve master instrument ids for CE/PE and fetch candles
                String ceKey = commonUtils.buildOptionSymbol(underlying, "currentMonth", strike, "CE");
                String peKey = commonUtils.buildOptionSymbol(underlying, "currentMonth", strike, "PE");

                MasterResponseFO ceMaster = null;
                MasterResponseFO peMaster = null;
                try {
                    ceMaster = masterCache.get(ceKey);
                } catch (ExecutionException ee) {
                    logger.warn("[{}] master cache load failed for {}: {}", underlying, ceKey, ee.getMessage());
                }
                try {
                    peMaster = masterCache.get(peKey);
                } catch (ExecutionException ee) {
                    logger.warn("[{}] master cache load failed for {}: {}", underlying, peKey, ee.getMessage());
                }


                if (ceMaster == null || peMaster == null) {
                    logger.warn("[{}] Missing master for strike {} | CE={} | PE={} - skipping strike",
                            underlying, strike, ceMaster == null, peMaster == null);
                    continue;
                }

                String ceId = String.valueOf(ceMaster.getExchangeInstrumentID());
                String peId = String.valueOf(peMaster.getExchangeInstrumentID());

                // Fetch candles (5m) for both legs - keep same lookback as before
                List<CandleData> ce5m = getCachedCandles(underlying, ceId, "5m", 70);
                List<CandleData> pe5m = getCachedCandles(underlying, peId, "5m", 70);

                // Detect accumulation for CE
                List<AccumSignal> ceSignals = checkAccumulation("5m", ce5m, true, VOLUME_MULTIPLIER_5M, strategy, underlying, strike);
                for (AccumSignal s : ceSignals) {
                    allSignals.add(new AccumSignalChoice(s, strike));
                }

                // Detect accumulation for PE
                List<AccumSignal> peSignals = checkAccumulation("5m", pe5m, false, VOLUME_MULTIPLIER_5M, strategy, underlying, strike);
                for (AccumSignal s : peSignals) {
                    allSignals.add(new AccumSignalChoice(s, strike));
                }

            } catch (Exception ex) {
                logger.error("[{}] Error scanning strike {} for accumulation - skipping: {}", underlying, strike, ex.getMessage());
            }
        }

        if (allSignals.isEmpty()) return Optional.empty();

        // 1) Try to find ATM signal (exact match)
        Optional<AccumSignalChoice> atmSignal = allSignals.stream()
                .filter(c -> Objects.equals(c.getStrike(), strikesToCheck.get((strikesToCheck.size()-1)/2))) // middle index = ATM
                .findFirst();

        if (atmSignal.isPresent()) return atmSignal;

        // 2) Otherwise choose by highest timeframe priority and then by highest volume multiplier
        Comparator<AccumSignalChoice> byPriorityThenVol = Comparator
                .comparingInt((AccumSignalChoice c) -> c.getSignal().getPriority()).reversed()
                .thenComparingDouble(c -> c.getSignal().getVolumeMultiplier()).reversed();

        return allSignals.stream().sorted(byPriorityThenVol).findFirst();
    }

    private List<AccumSignal> checkAccumulation(
            String timeframe,
            List<CandleData> candles,
            boolean isCall,
            double volThreshold,
            Strategy strategy,
            String underlying,
            int strike) {

        List<AccumSignal> result = new ArrayList<>();

        if (candles == null || candles.size() < 2) {
            logger.debug("[{}][{}] Skipping {} | Not enough candles (count={})",
                    underlying, timeframe, isCall ? "CE" : "PE",
                    candles != null ? candles.size() : 0);
            return result;
        }

        // Sort candles to ensure chronological order
        candles = candles.stream()
                .sorted(Comparator.comparingLong(CandleData::getEpochTime))
                .toList();

        // Pick last two candles
        CandleData prev = candles.get(candles.size() - 2);
        CandleData curr = candles.get(candles.size() - 1);

        long currEpoch = curr.getEpochTime();

        if (isWithinTimeframeBoundaryDebounce(timeframe, Duration.ofSeconds(5), underlying)) {
            return result;
        }

        // === Debounce check ===
        Instant candleEndInstant = Instant.ofEpochSecond(currEpoch - (5 * 3600 + 30 * 60)); // API gives IST-based epoch
        Instant now = Instant.now();

        Duration sinceCandleEnd = Duration.between(candleEndInstant, now);

        if (sinceCandleEnd.isNegative()) {
            logger.info("[{}][{}] Debounce active | CandleEnd={} (IST aligned) | Age={}s | Waiting for stable candle.",
                    underlying, timeframe, candleEndInstant, sinceCandleEnd.toSeconds());
            return result;
        }


        // === Deduplication: prevent multiple signals for same candle ===
        Hibernate.initialize(strategy.getStrategyAdditions());
        StrategyAdditions additions = strategy.getStrategyAdditions();
        if (additions == null) {
            additions = new StrategyAdditions();
            additions.setStrategy(strategy);
            strategy.setStrategyAdditions(additions);
        }

        Long lastProcessedEpoch = switch (timeframe) {
            case "1h" -> additions.getLastAccumulation1hBarTime();
            case "15m" -> additions.getLastAccumulation15mBarTime();
            case "5m" -> additions.getLastAccumulation5mBarTime();
            default -> null;
        };

        boolean sameCandle = lastProcessedEpoch != null &&
                Math.abs(currEpoch - lastProcessedEpoch / 1000) <= CANDLE_TIMESTAMP_TOLERANCE.getSeconds();

        if (sameCandle) {
            logger.info("[{}][{}] Duplicate candle detected | CurrentEpoch={} | LastProcessed={} | Skipping signal.",
                    underlying, timeframe, currEpoch, lastProcessedEpoch);
            return result;
        }

        Long exitTs = additions.getLastExitCandleTimestamp();

        if (exitTs != null && exitTs == currEpoch) {
            logger.info("[{}][{}] Entry not allowed in exit timestamp | Curr={} | Exit={}",
                    underlying, timeframe, currEpoch, exitTs);
            return result;
        }

        // === Accumulation Logic ===
        double pctChange = ((curr.getClose() - curr.getOpen()) / curr.getOpen()) * 100.0;
        double volMult = (curr.getVolume() - prev.getVolume()) / Math.max(prev.getVolume(), 1.0);

        boolean isAccum = pctChange > PRICE_THRESHOLD && volMult >= volThreshold;

        if (isAccum) {

            double currentPrice = curr.getClose();

            // Create signal FIRST
            AccumSignal signal = new AccumSignal(timeframe, isCall, volMult);

            // Get today's date in IST
            LocalDate todayIST = ZonedDateTime.now(ZONE_ID).toLocalDate();

            // Filter candles belonging to today (IST-safe)
            List<CandleData> sessionCandles = candles.stream()
                    .filter(c -> {
                        Instant instant = Instant.ofEpochSecond(c.getEpochTime());
                        LocalDate candleDateIST = instant.atZone(ZONE_ID).toLocalDate();
                        return candleDateIST.equals(todayIST);
                    })
                    .toList();

            int sessionCount = sessionCandles.size();
            Double vwap = null;

            if (sessionCount < MIN_CANDLES_FOR_VWAP) {

                logger.info("[{}][{}] VWAP SKIPPED | Strike={} | {} | SessionCandles={} (< {}) | Proceeding without VWAP filter",
                        underlying,
                        timeframe,
                        strike,
                        isCall ? "CE" : "PE",
                        sessionCount,
                        MIN_CANDLES_FOR_VWAP);

            } else {

                vwap = calculateVWAP(sessionCandles);
                boolean aboveVWAP = currentPrice > vwap * (1 + VWAP_MARGIN);

                logger.info("[{}][{}] VWAP CHECK | Strike={} | {} | Close={} | VWAP={} | AboveVWAP={} | SessionCandles={}",
                        underlying,
                        timeframe,
                        strike,
                        isCall ? "CE" : "PE",
                        String.format("%.2f", currentPrice),
                        String.format("%.2f", vwap),
                        aboveVWAP,
                        sessionCount);

                if (!aboveVWAP) {
                    logger.info("[{}][{}] ENTRY BLOCKED BY VWAP | Strike={} | {} | Close={} <= VWAP={}",
                            underlying,
                            timeframe,
                            strike,
                            isCall ? "CE" : "PE",
                            String.format("%.2f", currentPrice),
                            String.format("%.2f", vwap));
                    return result;
                }

                logger.info("[{}][{}] VWAP CONFIRMED | Proceeding with signal | Strike={} | {}",
                        underlying,
                        timeframe,
                        strike,
                        isCall ? "CE" : "PE");
            }

            // ADD SIGNAL AFTER VWAP DECISION
            result.add(signal);

            // LOG ACCUMULATION (VWAP may be null)
            logAccumulationSignalData(
                    strategy,
                    signal,
                    curr,
                    prev,
                    isCall,
                    pctChange,
                    volMult,
                    strike,
                    vwap != null ? vwap : 0.0
            );

            // === Update last processed candle timestamp ===
            switch (timeframe) {
                case "1h" -> additions.setLastAccumulation1hBarTime(currEpoch * 1000);
                case "15m" -> additions.setLastAccumulation15mBarTime(currEpoch * 1000);
                case "5m" -> additions.setLastAccumulation5mBarTime(currEpoch * 1000);
            }

            try {
                strategyRepository.save(strategy);
                logger.info("[{}][{}] Updated last processed candle timestamp",
                        underlying, timeframe);
            } catch (Exception e) {
                logger.error("[{}][{}] Failed to persist lastProcessedCandleTime for strategyId={}",
                        underlying, timeframe, strategy.getId(), e);
            }
        } else {
//            logger.info("[{}][{}] No accumulation | {} | PctChange={} | VolMult={} | Call={}",
//                    underlying, timeframe, isCall ? "CE" : "PE",
//                    String.format("%.2f", pctChange),
//                    String.format("%.2f", volMult),
//                    isCall);
        }

        return result;
    }

    private boolean isWithinTimeframeBoundaryDebounce(String timeframe, Duration debounceWindow, String underlying) {
        ZoneId IST = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(IST);

        int tfMinutes = switch (timeframe) {
            case "1h" -> 60;
            case "15m" -> 15;
            case "5m" -> 5;
            default -> 5; // Default safeguard
        };

        // Compute last timeframe boundary (e.g., 09:15:00, 09:30:00)
        ZonedDateTime boundaryIst = nowIst
                .truncatedTo(ChronoUnit.MINUTES)
                .withMinute((nowIst.getMinute() / tfMinutes) * tfMinutes)
                .withSecond(0)
                .withNano(0);

        Duration sinceBoundary = Duration.between(boundaryIst, nowIst);

        if (!sinceBoundary.isNegative() && sinceBoundary.compareTo(debounceWindow) < 0) {
            logger.info("[{}][{}] Boundary debounce active | Boundary={} | Age={}s | Window={}s | Waiting for candle stabilization.",
                    underlying, timeframe, boundaryIst, sinceBoundary.toSeconds(), debounceWindow.toSeconds());
            return true;
        }

        return false;
    }

    public boolean checkAtmOptionAccumulationExit(Strategy strategy) {
        try {
            // === Manual Exit ===
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                logger.info("[{}] Manual Exit Enabled — Triggering exit", strategy.getId());
                return true;
            }

            Hibernate.initialize(strategy.getUnderlying());
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            UnderlyingParams params = UnderlyingParams.resolve(underlying);

            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant scheduledExit = LocalDateTime.of(today,
                            LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime()))
                    .atZone(zoneId).toInstant();

            Instant now = Instant.now();

            // === Time-based Exit (default 15:05) ===
            if (now.isAfter(scheduledExit)) {
                logger.info("[{}] Scheduled exit time reached ({}). Triggering exit.", strategy.getId(), scheduledExit);
                return true;
            }

            // === Fetch Active Signal ===
            Long liveSignal = signalRepository.findLatestSignalId(strategy.getId(), Status.LIVE.getKey());
            if (liveSignal == null) {
                logger.info("[{}] No active LIVE signals found for exit check", strategy.getId());
                return false;
            }

            List<Object[]> rawLegRows = strategyLegRepository.findMinimalLegsBySignalId(liveSignal);

            if (rawLegRows == null || rawLegRows.isEmpty()) {
                logger.info("[{}] No strategy legs returned for signalId={} - skipping exit check", strategy.getId(), liveSignal);
                return false;
            }

            List<MinimalLegDto> legs = convertToMinimalLegDTOs(rawLegRows);

            for (MinimalLegDto leg : legs) {
                if (!Status.LIVE.getKey().equalsIgnoreCase(leg.getStatus())) continue;

                Long legId = leg.getId();
                Long strategyId = strategy.getId();
                RedisLegState state = legStateProvider.getLegState(strategyId, legId);
                if (state == null) {
                    logger.error("[{}] No redis/db state for legId={} - skipping", strategy.getId(), legId);
                    continue;
                }

                Double executedPrice = state.getExecutedPrice();
                Double baseStopLossPct = state.getStopLossPct(); // percent
                if (executedPrice == null || executedPrice <= 0) {
                    logger.warn("[{}] Invalid executed price for legId={} | Price={}", strategy.getId(), legId, executedPrice);
                    continue;
                }

                MarketData tick = touchLineService.getTouchLine(String.valueOf(leg.getExchangeInstrumentId()));
                if (tick == null || tick.getLTP() <= 0) {
                    logger.warn("[{}] Missing or invalid LTP for legId={} | InstrumentId={}", strategy.getId(), leg.getId(), leg.getExchangeInstrumentId());
                    continue;
                }

                double ltp = tick.getLTP();
                boolean isBuy = LegSide.BUY.getKey().equalsIgnoreCase(leg.getBuySellFlag());
                double pnlPercent = ((ltp - executedPrice) / executedPrice) * 100 * (isBuy ? 1 : -1);
                double priceLoss = (executedPrice - ltp) * (isBuy ? 1 : -1);


                // ------------- TRAILING STOP-LOSS (use Redis + distributed lock) --------------
                String legKey = redisLegService.legKey(strategyId, legId);
                RLock lock = redissonClient.getLock("lock:" + legKey);
                boolean locked = false;
                try {
                    locked = lock.tryLock(50, 500, TimeUnit.MILLISECONDS);
                    if (!locked) {
                        logger.error("[TSL] Could not acquire lock for {} - skipping TSL check", legKey);
                    } else {
                        // reload state now that we hold lock
                        state = legStateProvider.getLegState(strategyId, legId);
                        if (state == null) continue;

                        double currentPrice = ltp;
                        double executed = state.getExecutedPrice();
                        boolean activated = Boolean.TRUE.equals(state.getTslActive());
                        double trailingPoints = state.getTrailingDistance() == null ? params.trailingDistance : state.getTrailingDistance();
                        Double tslPoints = state.getTslPoints();
                        Double anchorPrice = state.getTslAnchor();

                        // Activation
                        double TSL_ACTIVATE_ABS = Math.min(params.tslActivateCap, executed * 0.10);
                        double TSL_STEP_ABS     = params.tslStepAbs;
                        double TSL_STEP_PCT     = 0.02; // 2%

                        double profitAbs = isBuy ? (currentPrice - executed) : (executed - currentPrice);

                        if (!activated && profitAbs >= TSL_ACTIVATE_ABS) {
                            double firstTsl = isBuy ? currentPrice - trailingPoints : currentPrice + trailingPoints;
                            state.setTslActive(true);
                            state.setTslPoints(firstTsl);
                            state.setTslAnchor(currentPrice);
                            state.setUpdatedAt(System.currentTimeMillis());
                            String underlyingKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
                            redisLegService.writeLegState(state, underlyingKey);
                            logger.info("[TSL] ACTIVATED | Leg={} | ProfitAbs={} | Anchor={} | TSL= {}",
                                    leg.getId(), profitAbs, currentPrice, firstTsl);
                            tslPoints = state.getTslPoints();
                            anchorPrice = state.getTslAnchor();
                            activated = true;
                        }

                        // Step movement
                        if (activated && anchorPrice != null) {
                            boolean stepCondition = isBuy
                                    ? (currentPrice >= anchorPrice * (1.0 + TSL_STEP_PCT)) || (currentPrice >= anchorPrice + TSL_STEP_ABS)
                                    : (currentPrice <= anchorPrice * (1.0 - TSL_STEP_PCT)) || (currentPrice <= anchorPrice - TSL_STEP_ABS);

                            if (stepCondition) {
                                double candidateTsl = isBuy ? currentPrice - trailingPoints : currentPrice + trailingPoints;
                                boolean improves = (tslPoints == null) || (isBuy ? candidateTsl > tslPoints : candidateTsl < tslPoints);
                                if (improves) {
                                    state.setTslPoints(candidateTsl);
                                    state.setTslAnchor(currentPrice);
                                    state.setUpdatedAt(System.currentTimeMillis());
                                    String underlyingKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
                                    redisLegService.writeLegState(state, underlyingKey);
                                    logger.info("[TSL] STEPPED | Leg={} | NewAnchor={} | NewTSL={}", leg.getId(), currentPrice, candidateTsl);
                                    tslPoints = state.getTslPoints();
                                    anchorPrice = state.getTslAnchor();
                                }
                            }
                        }

                        // Trigger check (TSL)
                        if (state.getTslActive() && tslPoints != null) {
                            boolean triggered = isBuy ? currentPrice <= tslPoints : currentPrice >= tslPoints;
                            if (triggered) {
                                // mark exit in redis to avoid duplicate triggers
                                state.setStatus("EXIT");
                                state.setUpdatedAt(System.currentTimeMillis());
                                String underlyingKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
                                redisLegService.writeLegState(state, underlyingKey);

                                logger.info("[TSL] TRIGGERED | leg={} | LTP={} | TSL={} | executing exit",
                                        leg.getId(), currentPrice, tslPoints);

                                // call your existing persistent exit flow (single place)
                                logExitSignalData(strategy, leg, "TSL Triggered", currentPrice, executedPrice);
                                try {
                                    String key = underlying + ":" + leg.getExchangeInstrumentId();
                                    List<CandleData> last5mCandle = candleRedisService.getCandles(key, "5m", 1);
                                    long exitEpochMillis = (last5mCandle.get(0).getEpochTime());
                                    updateLastExitSafe(strategy.getId(), exitEpochMillis);
                                } catch (Exception e) {
                                    logger.error("Failed to update lastExit timestamp for strategy {}", strategy.getId(), e);
                                }
                                // call exitStrategy which will create DB exit & grpc
                                return true; // top-level method should handle the exit
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("[TSL] Error during TSL check for legId={}", legId, e);
                } finally {
                    try {
                        if (locked) lock.unlock();
                    } catch (Exception ignore) {}
                }

                // ------------- STATIC / HYBRID STOP LOSS -------------
                double pctStopLossPts = executedPrice * (baseStopLossPct / 100.0);
                double effectiveStopLossPts = Math.min(params.absoluteStopLoss, pctStopLossPts);

                if (priceLoss >= effectiveStopLossPts) {
                    logger.info("[{}] Exit Triggered by Fixed SL | LegId={} | Entry={} | LTP={} | LossPts={} | Threshold={}",
                            strategy.getId(), leg.getId(),
                            String.format("%.2f", executedPrice),
                            String.format("%.2f", ltp),
                            String.format("%.2f", priceLoss),
                            String.format("%.2f", effectiveStopLossPts));
                    logExitSignalData(strategy, leg, "Fixed Stop Loss Triggered", ltp, executedPrice);
                    state.setStatus("EXIT");
                    state.setUpdatedAt(System.currentTimeMillis());
                    redisLegService.writeLegState(state, strategy.getUnderlying().getName());
                    try {
                        String key = underlying + ":" + leg.getExchangeInstrumentId();
                        List<CandleData> last5mCandle = candleRedisService.getCandles(key, "5m", 1);
                        long exitEpochMillis = (last5mCandle.get(0).getEpochTime());
                        updateLastExitSafe(strategy.getId(), exitEpochMillis);
                    } catch (Exception e) {
                        logger.error("Failed to update lastExit timestamp for strategy {}", strategy.getId(), e);
                    }
                    return true;
                }

                //Check if last 2 candles are distribution candles ===
                try {
                    String key = underlying + ":" + leg.getExchangeInstrumentId();
                    List<CandleData> last5mCandles = candleRedisService.getCandles(key, "5m", 5);

                    if (last5mCandles != null && last5mCandles.size() >= 2) {

                        long entryMs = state.getEntryTimestamp(); // stored at entry time

                        // Filter only candles formed AFTER entry
                        List<CandleData> validCandles = last5mCandles.stream()
                                .filter(c -> c.getEpochTime() * 1000L >= entryMs)
                                .sorted(Comparator.comparingLong(CandleData::getEpochTime))
                                .toList();

                        if (validCandles.size() >= 2) {
                            CandleData prev = validCandles.get(validCandles.size() - 2);
                            CandleData curr = validCandles.get(validCandles.size() - 1);

                            boolean prevDist = prev.getClose() < prev.getOpen();
                            boolean currDist = curr.getClose() < curr.getOpen();

                            if (prevDist && currDist) {
                                logger.info("[{}] EXIT: Distribution candles AFTER entry | LegId={} | Prev[O={},C={}] Curr[O={},C={}]",
                                        strategy.getId(), leg.getId(),
                                        prev.getOpen(), prev.getClose(),
                                        curr.getOpen(), curr.getClose());

                                logExitSignalData(strategy, leg, "Distribution Candle Exit", ltp, executedPrice);

                                // Mark exit in Redis
                                state.setStatus("EXIT");
                                state.setUpdatedAt(System.currentTimeMillis());
                                redisLegService.writeLegState(state, underlying);
                                try {
                                    List<CandleData> last5mCandle = candleRedisService.getCandles(key, "5m", 1);
                                    long exitEpochMillis = (last5mCandle.get(0).getEpochTime());
                                    updateLastExitSafe(strategy.getId(), exitEpochMillis);
                                } catch (Exception e) {
                                    logger.error("Failed to update lastExit timestamp for strategy {}", strategy.getId(), e);
                                }

                                return true;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("[{}] Error while checking 2-candle distribution exit for legId={}", strategy.getId(), leg.getId(), ex);
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("Error during checkAtmOptionAccumulationExit for strategyId={}", strategy.getId(), e);
            return false;
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            Signal exit = signalService.createExit(strategy);
            if (exit != null && !ExecutionTypeMenu.PAPER_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                grpcService.sendExitSignal(exit);
            }
            logger.info("ATM Option Accumulation strategy exited: {}", strategy.getId());
        } catch (Exception ex) {
            logger.error("Error exiting ATM Option Accumulation strategy: {}", strategy.getId(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void check(Strategy strategy) {
        try {
            if (strategyService.inHouseEntryCheck(strategy)) {
                if (isEntryBlockedTime()) {
                    return;
                }
                this.runStrategy(strategy);
            } else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                if (checkAtmOptionAccumulationExit(strategy)) {
                    this.exitStrategy(strategy);
                }
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Market data not available")) {
                // Log but don't mark strategy as failed - retry on next tick
                logger.warn("Skipping strategy {} due to market data unavailability, will retry on next tick: {}",
                        strategy.getId(), e.getMessage());
            } else {
                logger.error("Error in ATM Option Accumulation check() for strategyId={}", strategy.getId(), e);
            }
        } catch (Exception e) {
            logger.error("Error in ATM Option Accumulation check() for strategyId={}", strategy.getId(), e);
        }
    }

    private boolean isEntryBlockedTime() {
        LocalTime now = ZonedDateTime.now(ZONE_ID).toLocalTime();
        return !now.isBefore(ENTRY_BLOCK_START) && now.isBefore(ENTRY_BLOCK_END);
    }

    private double calculateVWAP(List<CandleData> candles) {
        double cumulativePV = 0.0;
        double cumulativeVolume = 0.0;

        for (CandleData c : candles) {
            double typicalPrice = (c.getHigh() + c.getLow() + c.getClose()) / 3.0;
            cumulativePV += typicalPrice * c.getVolume();
            cumulativeVolume += c.getVolume();
        }

        return cumulativeVolume == 0 ? 0 : cumulativePV / cumulativeVolume;
    }



    // === Inner DTO for Accumulation detection ===
    static class AccumSignal {
        private final String timeframe;
        private final boolean call;
        private final double volumeMultiplier;

        AccumSignal(String timeframe, boolean call, double volumeMultiplier) {
            this.timeframe = timeframe;
            this.call = call;
            this.volumeMultiplier = volumeMultiplier;
        }

        public String getTimeframe() { return timeframe; }
        public boolean isCall() { return call; }
        public double getVolumeMultiplier() { return volumeMultiplier; }

        public int getPriority() {
            return switch (timeframe) {
                case "1h" -> 3;
                case "15m" -> 2;
                default -> 1;
            };
        }
    }

    /**
     * Wrapper to carry a detected signal along with its strike
     */
    static class AccumSignalChoice {
        private final AccumSignal signal;
        private final int strike;

        AccumSignalChoice(AccumSignal signal, int strike) {
            this.signal = signal;
            this.strike = strike;
        }

        public AccumSignal getSignal() { return signal; }
        public int getStrike() { return strike; }
    }

    /**
     * Underlying-specific parameters for SL, TSL, and strike selection.
     * Sensex options have higher premiums than Nifty, so all absolute thresholds are scaled up.
     */
    static class UnderlyingParams {
        final double absoluteStopLoss;
        final double trailingDistance;
        final double tslActivateCap;
        final double tslStepAbs;
        final int strikeStep;

        UnderlyingParams(double absoluteStopLoss, double trailingDistance,
                         double tslActivateCap, double tslStepAbs, int strikeStep) {
            this.absoluteStopLoss = absoluteStopLoss;
            this.trailingDistance = trailingDistance;
            this.tslActivateCap = tslActivateCap;
            this.tslStepAbs = tslStepAbs;
            this.strikeStep = strikeStep;
        }

        static UnderlyingParams resolve(String underlying) {
            if (underlying != null && underlying.toUpperCase(Locale.ROOT).contains("SENSEX")) {
                // Sensex: ~2x Nifty thresholds
                return new UnderlyingParams(25.0, 8.0, 35.0, 8.0, 100);
            }
            // Nifty / default
            return new UnderlyingParams(15.0, 5.0, 15.0, 5.0, 50);
        }
    }

    private void logAccumulationSignalData(
            Strategy strategy,
            AccumSignal signal,
            CandleData currentCandle,
            CandleData previousCandle,
            boolean isCall,
            double pctChange,
            double volMult,
            int strike,
            double vwap) {
        try {
            String instrumentName = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            IndexInstruments instrument = IndexInstruments.fromKey(instrumentName);
            String instrumentId = instrument.getLabel().toString();

            String optionType = isCall ? "CALL (CE)" : "PUT (PE)";
            String timeframe = signal.getTimeframe();

            StringBuilder description = new StringBuilder();
            description.append("Accumulation Signal Created - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
//            description.append("Instrument: ").append(instrumentId).append("\n");
            description.append("Underlying: ").append(instrumentName).append("\n");
            description.append("Strike: ").append(strike).append("\n");
            description.append("Signal Type: ").append(optionType).append("\n");
            description.append("VWAP: ").append(String.format("%.2f", vwap)).append("\n");
            description.append("Close Above VWAP: ")
                    .append(currentCandle.getClose() > vwap)
                    .append("\n");
            description.append("Timeframe: ").append(timeframe).append("\n");
            description.append("Volume Multiplier Threshold: ").append(String.format("%.2f", signal.getVolumeMultiplier())).append("x\n");
            description.append("Detected Volume Multiplier: ").append(String.format("%.2f", volMult)).append("x\n");
            description.append("Price % Change: ").append(String.format("%.2f", pctChange)).append("%\n");
            description.append("Candle Open: ").append(currentCandle.getOpen()).append(", Close: ").append(currentCandle.getClose())
                    .append(", High: ").append(currentCandle.getHigh()).append(", Low: ").append(currentCandle.getLow()).append("\n");
            description.append("Prev Candle Volume: ").append(previousCandle.getVolume()).append(", Current Candle Volume: ").append(currentCandle.getVolume()).append("\n");
            description.append("Candle Time (Epoch): ").append(currentCandle.getEpochTime()).append("\n");

            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setStatus(Status.LIVE.getKey());
            deploymentErrors.setDescription(Collections.singletonList(description.toString()));
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

            logger.info(description.toString());

        } catch (Exception e) {
            logger.error("[{}] Error logging accumulation signal data", strategy.getUnderlying().getName(), e);
        }
    }

    private void logExitSignalData(Strategy strategy,
                                   MinimalLegDto leg,
                                   String reason,
                                   double ltp,
                                   double executedPrice) {
        try {
            String instrumentName = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            IndexInstruments instrument = IndexInstruments.fromKey(instrumentName);
            String instrumentId = instrument.getLabel().toString();

            StringBuilder description = new StringBuilder();
            description.append("Exit Triggered - Details:\n");
            description.append("Strategy ID: ").append(strategy.getId()).append("\n");
//            description.append("Instrument: ").append(instrumentId).append("\n");
            description.append("Underlying: ").append(instrumentName).append("\n");
            description.append("Leg ID: ").append(leg.getId()).append("\n");
            description.append("ExchangeInstrumentId: ").append(leg.getExchangeInstrumentId()).append("\n");
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

            logger.info(description.toString());

        } catch (Exception e) {
            logger.error("[{}] Error logging exit signal data", strategy.getId(), e);
        }
    }


    @Transactional
    public void updateLastExitSafe(Long strategyId, Long epoch) {
        strategyAdditionsRepository.updateLastExit(strategyId, epoch);
    }


    private List<MinimalLegDto> convertToMinimalLegDTOs(List<Object[]> rawLegRows) {
        return rawLegRows.stream().map(row -> {
            MinimalLegDto leg = new MinimalLegDto();
            // Adjust indexes to match the SELECT order in the repository query
            leg.setId(safeLong(row, 0));
            leg.setExchangeInstrumentId(safeLong(row, 1));
            leg.setBuySellFlag(safeString(row, 2));
            leg.setStatus(safeString(row, 3));
            return leg;
        }).collect(Collectors.toList());
    }

    // --- Small safe-casting helpers for mapping Object[] -> typed fields ---
    private static Long safeLong(Object[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length) return null;
        Object o = row[idx];
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong((String) o); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String safeString(Object[] row, int idx) {
        if (row == null || idx < 0 || idx >= row.length) return null;
        Object o = row[idx];
        return o == null ? null : String.valueOf(o);
    }
}
