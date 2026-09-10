package com.quantlab.signal.strategy.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.signal.dto.JbBankniftyExpiryDetectionResult;
import com.quantlab.signal.dto.redisDto.CandleData;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.CandleRedisService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Global Engine for JB Expiry Special Strategy (BANKNIFTY / NIFTY).
 *
 * Runs ONCE per 15-min candle boundary and computes:
 * - ATM Straddle combined premium (CE + PE)
 * - RSI on the combined straddle premium (14-period)
 * - Supertrend (10,3) on 15-min straddle price chart
 *
 * The result is cached in Redis per underlying and consumed by all strategy instances.
 * Uses distributed locking for single computation per candle.
 */
@Service
public class JbBankniftyExpiryEngineService {

    private static final Logger logger = LoggerFactory.getLogger(JbBankniftyExpiryEngineService.class);

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final String REDIS_KEY_PREFIX = "jb_banknifty_expiry:signal:";
    private static final String DETECTION_LOCK_PREFIX = "jb_bnf_expiry_detection:";

    // Detection Configuration
    private static final int CANDLE_INTERVAL_MINUTES = 15;
    private static final long CACHE_TTL_MILLIS = (CANDLE_INTERVAL_MINUTES * 60 * 1000) - 10_000; // 14m50s

    // RSI Configuration
    private static final int RSI_PERIOD = 14;
    private static final double RSI_ENTRY_THRESHOLD = 30.0;
    private static final int RSI_CANDLE_COUNT = 30; // 30 fifteen-minute candles

    // Supertrend Configuration
    private static final int SUPERTREND_ATR_PERIOD = 10;
    private static final double SUPERTREND_MULTIPLIER = 3.0;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private CandleRedisService candleRedisService;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
                    .maximumSize(20_000)
                    .build(new CacheLoader<>() {
                        @Override
                        public List<CandleData> load(String key) throws Exception {
                            return fetchCandlesFromKey(key);
                        }
                    });

    /**
     * Compute and store JB Banknifty Expiry detection result in Redis.
     * Uses distributed locking for idempotency.
     */
    public Optional<JbBankniftyExpiryDetectionResult> computeAndStore(Strategy sourceStrategy, String underlying) {
        logger.info("[JB-ENGINE][{}] computeAndStore called | sourceStrategyId={}", underlying, sourceStrategy.getId());

        Hibernate.initialize(sourceStrategy.getUnderlying());
        String redisKey = buildRedisKey(underlying);

        // Check cached result
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            try {
                JbBankniftyExpiryDetectionResult result = objectMapper.readValue(cached, JbBankniftyExpiryDetectionResult.class);
                if (result.isValid()) {
                    logger.info("[JB-ENGINE][{}] Using cached detection | RSI={} | SupertrendViolated={}",
                            underlying, String.format("%.2f", result.getRsiValue()), result.isPriceAboveSupertrend());
                    return Optional.of(result);
                }
            } catch (JsonProcessingException e) {
                logger.error("[JB-ENGINE][{}] Failed to deserialize cached result", underlying, e);
            }
        }

        // Acquire distributed lock
        String lockKey = DETECTION_LOCK_PREFIX + underlying;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(100, 30_000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                logger.info("[JB-ENGINE][{}] Could not acquire lock - another instance running", underlying);
                cached = redisTemplate.opsForValue().get(redisKey);
                if (cached != null) {
                    try {
                        return Optional.of(objectMapper.readValue(cached, JbBankniftyExpiryDetectionResult.class));
                    } catch (JsonProcessingException e) {
                        logger.error("[JB-ENGINE][{}] Deserialize failed after lock failure", underlying, e);
                    }
                }
                return Optional.empty();
            }

            logger.info("[JB-ENGINE][{}] Lock acquired - starting detection", underlying);

            // Double-check after lock
            cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                try {
                    JbBankniftyExpiryDetectionResult result = objectMapper.readValue(cached, JbBankniftyExpiryDetectionResult.class);
                    if (result.isValid()) {
                        return Optional.of(result);
                    }
                } catch (JsonProcessingException e) {
                    logger.error("[JB-ENGINE][{}] Deserialize failed in double-check", underlying, e);
                }
            }

            // Run detection
            JbBankniftyExpiryDetectionResult result = runDetection(sourceStrategy, underlying);

            // Store in Redis
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(redisKey, json, CANDLE_INTERVAL_MINUTES, TimeUnit.MINUTES);
                logger.info("[JB-ENGINE][{}] Result stored | RSI={} | RsiTriggered={} | SupertrendViolated={} | ATM={}",
                        underlying, String.format("%.2f", result.getRsiValue()),
                        result.isRsiSignalTriggered(), result.isPriceAboveSupertrend(), result.getAtmStrike());
            } catch (JsonProcessingException e) {
                logger.error("[JB-ENGINE][{}] CRITICAL: Failed to serialize result", underlying, e);
            }

            return Optional.of(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[JB-ENGINE][{}] Detection interrupted", underlying, e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("[JB-ENGINE][{}] Unexpected error during detection", underlying, e);
            return Optional.empty();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Get cached detection result from Redis.
     */
    public Optional<JbBankniftyExpiryDetectionResult> getSignal(String underlying) {
        String redisKey = buildRedisKey(underlying);
        String cached = redisTemplate.opsForValue().get(redisKey);

        if (cached == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cached, JbBankniftyExpiryDetectionResult.class));
        } catch (JsonProcessingException e) {
            logger.error("[JB-ENGINE][{}] Failed to deserialize cached signal", underlying, e);
            return Optional.empty();
        }
    }

    private String buildRedisKey(String underlying) {
        return REDIS_KEY_PREFIX + underlying.toUpperCase();
    }

    /**
     * Core detection logic:
     * 1. Get ATM strike from synthetic price
     * 2. Build combined straddle candles (CE + PE)
     * 3. Calculate RSI on straddle premium
     * 4. Calculate Supertrend (10,3) on straddle candles
     */
    private JbBankniftyExpiryDetectionResult runDetection(Strategy sourceStrategy, String underlying) {
        Instant now = Instant.now();
        long ttlExpiry = now.plusMillis(CACHE_TTL_MILLIS).toEpochMilli();

        logger.info("[JB-ENGINE][{}] === STARTING DETECTION ===", underlying);

        try {
            // Step 1: Get ATM strike
            double syntheticPrice = marketDataFetch.getSyntheticPrice(sourceStrategy, underlying);
            String expiryDate = commonUtils.getExpiryShotDateByIndex(
                    sourceStrategy.getExpiry(), underlying, OptionType.OPTION.getKey());
            int atmStrike = marketDataFetch.getATM(underlying, (int) syntheticPrice, expiryDate);

            logger.info("[JB-ENGINE][{}] ATM calculated | Synthetic={} | ATM={}",
                    underlying, String.format("%.2f", syntheticPrice), atmStrike);

            // Step 2: Get CE and PE master data and current prices
            String ceKey = underlying.toUpperCase() + expiryDate + "-" + atmStrike + "CE";
            String peKey = underlying.toUpperCase() + expiryDate + "-" + atmStrike + "PE";

            MasterResponseFO ceMaster;
            MasterResponseFO peMaster;
            try {
                ceMaster = masterCache.get(ceKey);
                peMaster = masterCache.get(peKey);
            } catch (ExecutionException e) {
                logger.error("[JB-ENGINE][{}] Failed to get master data | CE={} | PE={}", underlying, ceKey, peKey, e);
                return buildEmptyResult(underlying, now, ttlExpiry);
            }

            String ceInstrumentId = String.valueOf(ceMaster.getExchangeInstrumentID());
            String peInstrumentId = String.valueOf(peMaster.getExchangeInstrumentID());

            MarketData ceTouchline = touchLineService.getTouchLine(ceInstrumentId);
            MarketData peTouchline = touchLineService.getTouchLine(peInstrumentId);

            if (ceTouchline == null || peTouchline == null) {
                logger.warn("[JB-ENGINE][{}] Invalid touchline data", underlying);
                return buildEmptyResult(underlying, now, ttlExpiry);
            }

            double cePremium = ceTouchline.getLTP();
            double pePremium = peTouchline.getLTP();

            // Step 3: Build combined straddle candles from CE and PE 15-min candles
            List<CandleData> ceCandles = getCachedCandles(underlying, ceInstrumentId, "15m", RSI_CANDLE_COUNT);
            List<CandleData> peCandles = getCachedCandles(underlying, peInstrumentId, "15m", RSI_CANDLE_COUNT);

            List<CandleData> straddleCandles = buildStraddleCandles(ceCandles, peCandles);

            if (straddleCandles.size() < RSI_PERIOD + 1) {
                logger.warn("[JB-ENGINE][{}] Not enough straddle candles for RSI | Count={} | Required={}",
                        underlying, straddleCandles.size(), RSI_PERIOD + 1);
                return buildEmptyResult(underlying, now, ttlExpiry, atmStrike, cePremium, pePremium, syntheticPrice);
            }

            // Step 4: Calculate RSI on straddle close prices
            double rsiValue = calculateRSI(straddleCandles, RSI_PERIOD);
            boolean rsiTriggered = rsiValue > 0 && rsiValue < RSI_ENTRY_THRESHOLD;

            logger.info("[JB-ENGINE][{}] RSI calculated | RSI={} | Triggered={} | Threshold={}",
                    underlying, String.format("%.2f", rsiValue), rsiTriggered, RSI_ENTRY_THRESHOLD);

            // Step 5: Calculate Supertrend (10,3) on straddle candles
            double[] supertrendResult = calculateSupertrend(straddleCandles, SUPERTREND_ATR_PERIOD, SUPERTREND_MULTIPLIER);
            double supertrendValue = supertrendResult[0];
            boolean priceAboveSupertrend = supertrendResult[1] > 0; // 1 = above, -1 = below

            double lastClose = straddleCandles.get(straddleCandles.size() - 1).getClose();

            logger.info("[JB-ENGINE][{}] Supertrend calculated | Value={} | StraddelClose={} | AboveST={}",
                    underlying, String.format("%.2f", supertrendValue),
                    String.format("%.2f", lastClose), priceAboveSupertrend);

            // Get candle epoch time
            long candleEpoch = straddleCandles.get(straddleCandles.size() - 1).getEpochTime();

            logger.info("[JB-ENGINE][{}] === DETECTION COMPLETE === | RSI={} | RsiTriggered={} | AboveST={} | ATM={}",
                    underlying, String.format("%.2f", rsiValue), rsiTriggered, priceAboveSupertrend, atmStrike);

            return JbBankniftyExpiryDetectionResult.builder()
                    .underlying(underlying)
                    .detectionTime(now)
                    .candleEpochTime(candleEpoch)
                    .rsiSignalTriggered(rsiTriggered)
                    .rsiValue(rsiValue)
                    .priceAboveSupertrend(priceAboveSupertrend)
                    .supertrendValue(supertrendValue)
                    .straddelClosePrice(lastClose)
                    .atmStrike(atmStrike)
                    .cePremium(cePremium)
                    .pePremium(pePremium)
                    .currentMarketPrice(syntheticPrice)
                    .ttlExpiryMillis(ttlExpiry)
                    .build();

        } catch (Exception e) {
            logger.error("[JB-ENGINE][{}] CRITICAL: Detection failed", underlying, e);
            return buildEmptyResult(underlying, now, ttlExpiry);
        }
    }

    /**
     * Build combined straddle candles by merging CE and PE candles on matching epoch times.
     * Combined OHLC = CE_OHLC + PE_OHLC
     */
    private List<CandleData> buildStraddleCandles(List<CandleData> ceCandles, List<CandleData> peCandles) {
        if (ceCandles == null || peCandles == null || ceCandles.isEmpty() || peCandles.isEmpty()) {
            return List.of();
        }

        // Index PE candles by epoch for O(1) lookup
        Map<Long, CandleData> peByEpoch = peCandles.stream()
                .collect(Collectors.toMap(CandleData::getEpochTime, c -> c, (a, b) -> b));

        List<CandleData> combined = new ArrayList<>();
        for (CandleData ce : ceCandles) {
            CandleData pe = peByEpoch.get(ce.getEpochTime());
            if (pe != null) {
                combined.add(CandleData.builder()
                        .epochTime(ce.getEpochTime())
                        .open(ce.getOpen() + pe.getOpen())
                        .high(ce.getHigh() + pe.getHigh())
                        .low(ce.getLow() + pe.getLow())
                        .close(ce.getClose() + pe.getClose())
                        .volume(ce.getVolume() + pe.getVolume())
                        .build());
            }
        }

        // Sort chronologically
        combined.sort(Comparator.comparingLong(CandleData::getEpochTime));

        logger.debug("[JB-ENGINE] Built {} straddle candles from {} CE and {} PE candles",
                combined.size(), ceCandles.size(), peCandles.size());

        return combined;
    }

    /**
     * Calculate RSI (Relative Strength Index) using Wilder's smoothing method.
     *
     * RSI = 100 - (100 / (1 + RS))
     * RS = Average Gain / Average Loss
     *
     * @param candles Sorted chronologically
     * @param period RSI period (typically 14)
     * @return RSI value (0-100), or -1 if insufficient data
     */
    private double calculateRSI(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period + 1) {
            return -1.0;
        }

        // Calculate price changes (close-to-close)
        double[] changes = new double[candles.size() - 1];
        for (int i = 1; i < candles.size(); i++) {
            changes[i - 1] = candles.get(i).getClose() - candles.get(i - 1).getClose();
        }

        // First average gain and loss (SMA for initial period)
        double avgGain = 0.0;
        double avgLoss = 0.0;

        for (int i = 0; i < period; i++) {
            if (changes[i] > 0) {
                avgGain += changes[i];
            } else {
                avgLoss += Math.abs(changes[i]);
            }
        }
        avgGain /= period;
        avgLoss /= period;

        // Wilder's smoothing for remaining periods
        for (int i = period; i < changes.length; i++) {
            double gain = changes[i] > 0 ? changes[i] : 0;
            double loss = changes[i] < 0 ? Math.abs(changes[i]) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) {
            return 100.0;
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Calculate Supertrend (period, multiplier) indicator.
     *
     * Basic Upper Band = (H + L) / 2 + multiplier * ATR
     * Basic Lower Band = (H + L) / 2 - multiplier * ATR
     *
     * Uses Wilder's ATR smoothing.
     *
     * @return double[2]: [supertrendValue, direction] where direction: 1 = price above ST, -1 = price below ST
     */
    private double[] calculateSupertrend(List<CandleData> candles, int atrPeriod, double multiplier) {
        if (candles == null || candles.size() < atrPeriod + 1) {
            return new double[]{0.0, -1.0};
        }

        int n = candles.size();

        // Step 1: Calculate True Range
        double[] trueRange = new double[n];
        trueRange[0] = candles.get(0).getHigh() - candles.get(0).getLow();

        for (int i = 1; i < n; i++) {
            double hl = candles.get(i).getHigh() - candles.get(i).getLow();
            double hc = Math.abs(candles.get(i).getHigh() - candles.get(i - 1).getClose());
            double lc = Math.abs(candles.get(i).getLow() - candles.get(i - 1).getClose());
            trueRange[i] = Math.max(hl, Math.max(hc, lc));
        }

        // Step 2: Calculate ATR using Wilder's smoothing
        double[] atr = new double[n];

        // Initial ATR = SMA of first atrPeriod TRs
        double sumTR = 0;
        for (int i = 0; i < atrPeriod; i++) {
            sumTR += trueRange[i];
        }
        atr[atrPeriod - 1] = sumTR / atrPeriod;

        // Wilder's smoothing for remaining
        for (int i = atrPeriod; i < n; i++) {
            atr[i] = (atr[i - 1] * (atrPeriod - 1) + trueRange[i]) / atrPeriod;
        }

        // Step 3: Calculate Supertrend
        double[] upperBand = new double[n];
        double[] lowerBand = new double[n];
        double[] supertrend = new double[n];
        int[] direction = new int[n]; // 1 = uptrend (price above), -1 = downtrend (price below)

        // Initialize from ATR period
        int startIdx = atrPeriod - 1;
        double midPrice = (candles.get(startIdx).getHigh() + candles.get(startIdx).getLow()) / 2.0;
        upperBand[startIdx] = midPrice + multiplier * atr[startIdx];
        lowerBand[startIdx] = midPrice - multiplier * atr[startIdx];
        supertrend[startIdx] = upperBand[startIdx]; // start in downtrend
        direction[startIdx] = -1;

        for (int i = startIdx + 1; i < n; i++) {
            midPrice = (candles.get(i).getHigh() + candles.get(i).getLow()) / 2.0;

            double basicUpper = midPrice + multiplier * atr[i];
            double basicLower = midPrice - multiplier * atr[i];

            // Final Upper Band: if basicUpper < prev upperBand OR prev close > prev upperBand, use basicUpper
            if (basicUpper < upperBand[i - 1] || candles.get(i - 1).getClose() > upperBand[i - 1]) {
                upperBand[i] = basicUpper;
            } else {
                upperBand[i] = upperBand[i - 1];
            }

            // Final Lower Band: if basicLower > prev lowerBand OR prev close < prev lowerBand, use basicLower
            if (basicLower > lowerBand[i - 1] || candles.get(i - 1).getClose() < lowerBand[i - 1]) {
                lowerBand[i] = basicLower;
            } else {
                lowerBand[i] = lowerBand[i - 1];
            }

            // Determine direction
            if (direction[i - 1] == -1) {
                // Was in downtrend
                if (candles.get(i).getClose() > upperBand[i]) {
                    direction[i] = 1; // flip to uptrend
                    supertrend[i] = lowerBand[i];
                } else {
                    direction[i] = -1;
                    supertrend[i] = upperBand[i];
                }
            } else {
                // Was in uptrend
                if (candles.get(i).getClose() < lowerBand[i]) {
                    direction[i] = -1; // flip to downtrend
                    supertrend[i] = upperBand[i];
                } else {
                    direction[i] = 1;
                    supertrend[i] = lowerBand[i];
                }
            }
        }

        int lastIdx = n - 1;
        // direction[lastIdx] == 1 means price is above supertrend (uptrend)
        // For straddle: price above supertrend = violation (straddle premium rising = bad for short)
        return new double[]{supertrend[lastIdx], direction[lastIdx]};
    }

    private JbBankniftyExpiryDetectionResult buildEmptyResult(String underlying, Instant now, long ttlExpiry) {
        return JbBankniftyExpiryDetectionResult.builder()
                .underlying(underlying)
                .detectionTime(now)
                .rsiSignalTriggered(false)
                .rsiValue(-1)
                .priceAboveSupertrend(false)
                .supertrendValue(0)
                .ttlExpiryMillis(ttlExpiry)
                .build();
    }

    private JbBankniftyExpiryDetectionResult buildEmptyResult(String underlying, Instant now, long ttlExpiry,
                                                              int atmStrike, double cePremium, double pePremium,
                                                              double marketPrice) {
        return JbBankniftyExpiryDetectionResult.builder()
                .underlying(underlying)
                .detectionTime(now)
                .rsiSignalTriggered(false)
                .rsiValue(-1)
                .priceAboveSupertrend(false)
                .supertrendValue(0)
                .atmStrike(atmStrike)
                .cePremium(cePremium)
                .pePremium(pePremium)
                .currentMarketPrice(marketPrice)
                .ttlExpiryMillis(ttlExpiry)
                .build();
    }

    private List<CandleData> getCachedCandles(String underlying, String instrumentId, String timeframe, int count) {
        String cacheKey = underlying + ":" + instrumentId + ":" + timeframe + ":" + count;
        try {
            return candleCache.get(cacheKey);
        } catch (ExecutionException e) {
            logger.error("[JB-ENGINE][{}] Candle cache load failed | Key={}", underlying, cacheKey, e);
            return candleRedisService.getCandles(underlying + ":" + instrumentId, timeframe, count);
        }
    }

    private List<CandleData> fetchCandlesFromKey(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) {
                logger.error("[JB-ENGINE] Invalid candle cache key format | Key={}", key);
                return List.of();
            }
            String underlying = parts[0];
            String instrumentId = parts[1];
            String timeframe = parts[2];
            int count = Integer.parseInt(parts[3]);
            String redisKey = underlying + ":" + instrumentId;
            return candleRedisService.getCandles(redisKey, timeframe, count);
        } catch (Exception ex) {
            logger.error("[JB-ENGINE] Error parsing candle cache key | Key={}", key, ex);
            return List.of();
        }
    }
}
