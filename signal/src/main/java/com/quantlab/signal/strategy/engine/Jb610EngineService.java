package com.quantlab.signal.strategy.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.signal.dto.Jb610DetectionResult;
import com.quantlab.signal.dto.redisDto.CandleData;
import com.quantlab.signal.service.redisService.CandleRedisService;
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

/**
 * Global Engine for JB610 Dual Supertrend Crossover Strategy.
 *
 * Runs ONCE per 30-min candle boundary and computes:
 * - Supertrend (6,1)  — fast trigger
 * - Supertrend (10,3) — slow confirmation
 *
 * Crossover signals (on BANKNIFTY INDEX 30-min candles):
 * - BULLISH: Fast ST crosses above Slow ST → Bull Put Spread
 * - BEARISH: Fast ST crosses below Slow ST → Bear Call Spread
 *
 * Result is cached in Redis per underlying and consumed by all strategy instances.
 * Uses distributed locking for single computation per candle.
 */
@Service
public class Jb610EngineService {

    private static final Logger logger = LoggerFactory.getLogger(Jb610EngineService.class);

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final String REDIS_KEY_PREFIX = "jb610:signal:";
    private static final String DETECTION_LOCK_PREFIX = "jb610_detection:";

    // Detection Configuration
    private static final int CANDLE_INTERVAL_MINUTES = 30;
    private static final long CACHE_TTL_MILLIS = (CANDLE_INTERVAL_MINUTES * 60 * 1000) - 10_000; // 29m50s

    // Fast Supertrend Configuration: ST(6,1)
    private static final int FAST_ST_ATR_PERIOD = 6;
    private static final double FAST_ST_MULTIPLIER = 1.0;

    // Slow Supertrend Configuration: ST(10,3)
    private static final int SLOW_ST_ATR_PERIOD = 10;
    private static final double SLOW_ST_MULTIPLIER = 3.0;

    // Minimum candles needed for computation
    private static final int MIN_CANDLE_COUNT = 20; // 20 thirty-minute candles

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private CandleRedisService candleRedisService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
     * Compute and store JB610 detection result in Redis.
     * Uses distributed locking for idempotency.
     */
    public Optional<Jb610DetectionResult> computeAndStore(Strategy sourceStrategy, String underlying) {
        logger.info("[JB610-ENGINE][{}] computeAndStore called | sourceStrategyId={}", underlying, sourceStrategy.getId());

        Hibernate.initialize(sourceStrategy.getUnderlying());
        String redisKey = buildRedisKey(underlying);

        // Check cached result
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            try {
                Jb610DetectionResult result = objectMapper.readValue(cached, Jb610DetectionResult.class);
                if (result.isValid()) {
                    logger.info("[JB610-ENGINE][{}] Using cached detection | Crossover={} | Direction={}",
                            underlying, result.isCrossoverDetected(), result.getDirection());
                    return Optional.of(result);
                }
            } catch (JsonProcessingException e) {
                logger.error("[JB610-ENGINE][{}] Failed to deserialize cached result", underlying, e);
            }
        }

        // Acquire distributed lock
        String lockKey = DETECTION_LOCK_PREFIX + underlying;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(100, 30_000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                logger.info("[JB610-ENGINE][{}] Could not acquire lock - another instance running", underlying);
                cached = redisTemplate.opsForValue().get(redisKey);
                if (cached != null) {
                    try {
                        return Optional.of(objectMapper.readValue(cached, Jb610DetectionResult.class));
                    } catch (JsonProcessingException e) {
                        logger.error("[JB610-ENGINE][{}] Deserialize failed after lock failure", underlying, e);
                    }
                }
                return Optional.empty();
            }

            logger.info("[JB610-ENGINE][{}] Lock acquired - starting detection", underlying);

            // Double-check after lock
            cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                try {
                    Jb610DetectionResult result = objectMapper.readValue(cached, Jb610DetectionResult.class);
                    if (result.isValid()) {
                        return Optional.of(result);
                    }
                } catch (JsonProcessingException e) {
                    logger.error("[JB610-ENGINE][{}] Deserialize failed in double-check", underlying, e);
                }
            }

            // Run detection
            Jb610DetectionResult result = runDetection(sourceStrategy, underlying);

            // Store in Redis
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(redisKey, json, CANDLE_INTERVAL_MINUTES, TimeUnit.MINUTES);
                logger.info("[JB610-ENGINE][{}] Result stored | Crossover={} | Direction={} | FastST={} | SlowST={}",
                        underlying, result.isCrossoverDetected(), result.getDirection(),
                        String.format("%.2f", result.getFastSupertrendValue()),
                        String.format("%.2f", result.getSlowSupertrendValue()));
            } catch (JsonProcessingException e) {
                logger.error("[JB610-ENGINE][{}] CRITICAL: Failed to serialize result", underlying, e);
            }

            return Optional.of(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("[JB610-ENGINE][{}] Detection interrupted", underlying, e);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("[JB610-ENGINE][{}] Unexpected error during detection", underlying, e);
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
    public Optional<Jb610DetectionResult> getSignal(String underlying) {
        String redisKey = buildRedisKey(underlying);
        String cached = redisTemplate.opsForValue().get(redisKey);

        if (cached == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cached, Jb610DetectionResult.class));
        } catch (JsonProcessingException e) {
            logger.error("[JB610-ENGINE][{}] Failed to deserialize cached signal", underlying, e);
            return Optional.empty();
        }
    }

    private String buildRedisKey(String underlying) {
        return REDIS_KEY_PREFIX + underlying.toUpperCase();
    }

    /**
     * Core detection logic:
     * 1. Fetch BANKNIFTY INDEX 30-min candles
     * 2. Compute Fast Supertrend (6,1)
     * 3. Compute Slow Supertrend (10,3)
     * 4. Detect crossover between fast and slow
     * 5. Get current spot price
     */
    private Jb610DetectionResult runDetection(Strategy sourceStrategy, String underlying) {
        Instant now = Instant.now();
        long ttlExpiry = now.plusMillis(CACHE_TTL_MILLIS).toEpochMilli();

        logger.info("[JB610-ENGINE][{}] === STARTING DETECTION ===", underlying);

        try {
            // Step 1: Get index instrument ID
            int instrumentId = IndexInstruments.fromKey(underlying).getLabel();
            String instrumentIdStr = String.valueOf(instrumentId);

            // Step 2: Fetch 30-min INDEX candles
            List<CandleData> candles = getCachedCandles(underlying, instrumentIdStr, "30m", MIN_CANDLE_COUNT);

            if (candles == null || candles.size() < SLOW_ST_ATR_PERIOD + 2) {
                logger.warn("[JB610-ENGINE][{}] Not enough candles | Count={} | Required={}",
                        underlying, candles == null ? 0 : candles.size(), SLOW_ST_ATR_PERIOD + 2);
                return buildEmptyResult(underlying, now, ttlExpiry);
            }

            // Ensure chronological order
            candles.sort(Comparator.comparingLong(CandleData::getEpochTime));

            logger.info("[JB610-ENGINE][{}] Candles fetched | Count={} | FirstEpoch={} | LastEpoch={}",
                    underlying, candles.size(),
                    candles.get(0).getEpochTime(),
                    candles.get(candles.size() - 1).getEpochTime());

            // Step 3: Compute Fast Supertrend (6,1) — full array
            double[][] fastST = calculateSupertrendFull(candles, FAST_ST_ATR_PERIOD, FAST_ST_MULTIPLIER);

            // Step 4: Compute Slow Supertrend (10,3) — full array
            double[][] slowST = calculateSupertrendFull(candles, SLOW_ST_ATR_PERIOD, SLOW_ST_MULTIPLIER);

            if (fastST == null || slowST == null) {
                logger.warn("[JB610-ENGINE][{}] Supertrend computation failed", underlying);
                return buildEmptyResult(underlying, now, ttlExpiry);
            }

            int lastIdx = candles.size() - 1;
            int prevIdx = lastIdx - 1;

            double fastCurrent = fastST[lastIdx][0];
            double slowCurrent = slowST[lastIdx][0];
            double fastPrev = fastST[prevIdx][0];
            double slowPrev = slowST[prevIdx][0];

            // Step 5: Detect crossover
            boolean crossoverDetected = false;
            String direction = "NONE";

            // BULLISH crossover: fast was below slow, now fast is above slow
            boolean prevFastBelowSlow = fastPrev < slowPrev;
            boolean currFastAboveSlow = fastCurrent > slowCurrent;

            // BEARISH crossover: fast was above slow, now fast is below slow
            boolean prevFastAboveSlow = fastPrev > slowPrev;
            boolean currFastBelowSlow = fastCurrent < slowCurrent;

            if (prevFastBelowSlow && currFastAboveSlow) {
                crossoverDetected = true;
                direction = "BULLISH";
            } else if (prevFastAboveSlow && currFastBelowSlow) {
                crossoverDetected = true;
                direction = "BEARISH";
            }

            // Step 6: Get current spot price
            double spotPrice = marketDataFetch.getSyntheticPrice(sourceStrategy, underlying);

            long candleEpoch = candles.get(lastIdx).getEpochTime();

            logger.info("[JB610-ENGINE][{}] === DETECTION COMPLETE === | Crossover={} | Direction={} | FastST={} | SlowST={} | PrevFastST={} | PrevSlowST={} | Spot={}",
                    underlying, crossoverDetected, direction,
                    String.format("%.2f", fastCurrent), String.format("%.2f", slowCurrent),
                    String.format("%.2f", fastPrev), String.format("%.2f", slowPrev),
                    String.format("%.2f", spotPrice));

            return Jb610DetectionResult.builder()
                    .underlying(underlying)
                    .detectionTime(now)
                    .candleEpochTime(candleEpoch)
                    .crossoverDetected(crossoverDetected)
                    .direction(direction)
                    .fastSupertrendValue(fastCurrent)
                    .slowSupertrendValue(slowCurrent)
                    .prevFastSupertrendValue(fastPrev)
                    .prevSlowSupertrendValue(slowPrev)
                    .spotPrice(spotPrice)
                    .ttlExpiryMillis(ttlExpiry)
                    .build();

        } catch (Exception e) {
            logger.error("[JB610-ENGINE][{}] CRITICAL: Detection failed", underlying, e);
            return buildEmptyResult(underlying, now, ttlExpiry);
        }
    }

    /**
     * Calculate Supertrend for all candles, returning full array of [value, direction].
     *
     * @return double[n][2]: each entry is [supertrendValue, direction] where direction: 1=uptrend, -1=downtrend.
     *         Returns null if insufficient data.
     */
    private double[][] calculateSupertrendFull(List<CandleData> candles, int atrPeriod, double multiplier) {
        if (candles == null || candles.size() < atrPeriod + 1) {
            return null;
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
        double sumTR = 0;
        for (int i = 0; i < atrPeriod; i++) {
            sumTR += trueRange[i];
        }
        atr[atrPeriod - 1] = sumTR / atrPeriod;

        for (int i = atrPeriod; i < n; i++) {
            atr[i] = (atr[i - 1] * (atrPeriod - 1) + trueRange[i]) / atrPeriod;
        }

        // Step 3: Calculate Supertrend
        double[] upperBand = new double[n];
        double[] lowerBand = new double[n];
        double[][] result = new double[n][2]; // [value, direction]

        int startIdx = atrPeriod - 1;
        double midPrice = (candles.get(startIdx).getHigh() + candles.get(startIdx).getLow()) / 2.0;
        upperBand[startIdx] = midPrice + multiplier * atr[startIdx];
        lowerBand[startIdx] = midPrice - multiplier * atr[startIdx];
        result[startIdx][0] = upperBand[startIdx]; // start in downtrend
        result[startIdx][1] = -1;

        for (int i = startIdx + 1; i < n; i++) {
            midPrice = (candles.get(i).getHigh() + candles.get(i).getLow()) / 2.0;

            double basicUpper = midPrice + multiplier * atr[i];
            double basicLower = midPrice - multiplier * atr[i];

            // Final Upper Band
            if (basicUpper < upperBand[i - 1] || candles.get(i - 1).getClose() > upperBand[i - 1]) {
                upperBand[i] = basicUpper;
            } else {
                upperBand[i] = upperBand[i - 1];
            }

            // Final Lower Band
            if (basicLower > lowerBand[i - 1] || candles.get(i - 1).getClose() < lowerBand[i - 1]) {
                lowerBand[i] = basicLower;
            } else {
                lowerBand[i] = lowerBand[i - 1];
            }

            // Determine direction
            int prevDir = (int) result[i - 1][1];
            if (prevDir == -1) {
                if (candles.get(i).getClose() > upperBand[i]) {
                    result[i][1] = 1;  // flip to uptrend
                    result[i][0] = lowerBand[i];
                } else {
                    result[i][1] = -1;
                    result[i][0] = upperBand[i];
                }
            } else {
                if (candles.get(i).getClose() < lowerBand[i]) {
                    result[i][1] = -1; // flip to downtrend
                    result[i][0] = upperBand[i];
                } else {
                    result[i][1] = 1;
                    result[i][0] = lowerBand[i];
                }
            }
        }

        return result;
    }

    private Jb610DetectionResult buildEmptyResult(String underlying, Instant now, long ttlExpiry) {
        return Jb610DetectionResult.builder()
                .underlying(underlying)
                .detectionTime(now)
                .crossoverDetected(false)
                .direction("NONE")
                .ttlExpiryMillis(ttlExpiry)
                .build();
    }

    private List<CandleData> getCachedCandles(String underlying, String instrumentId, String timeframe, int count) {
        String cacheKey = underlying + ":" + instrumentId + ":" + timeframe + ":" + count;
        try {
            return candleCache.get(cacheKey);
        } catch (ExecutionException e) {
            logger.error("[JB610-ENGINE][{}] Candle cache load failed | Key={}", underlying, cacheKey, e);
            return candleRedisService.getCandles(underlying + ":" + instrumentId, timeframe, count);
        }
    }

    private List<CandleData> fetchCandlesFromKey(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) {
                logger.error("[JB610-ENGINE] Invalid candle cache key format | Key={}", key);
                return List.of();
            }
            String underlying = parts[0];
            String instrumentId = parts[1];
            String timeframe = parts[2];
            int count = Integer.parseInt(parts[3]);
            String redisKey = underlying + ":" + instrumentId;
            return candleRedisService.getCandles(redisKey, timeframe, count);
        } catch (Exception ex) {
            logger.error("[JB610-ENGINE] Error parsing candle cache key | Key={}", key, ex);
            return List.of();
        }
    }

    /**
     * Check if daily ST(6,1) has been breached (direction flip on last completed daily candle).
     * Returns true if exit should be triggered.
     *
     * Per strategy spec: "If Supertrend (6,1) is breached by candle close on Daily chart,
     * exit all positions. No intraday stop-loss. Only daily closing confirmation."
     *
     * IMPORTANT: Today's incomplete daily candle is excluded from the check.
     * Only fully closed daily candles are evaluated, because the requirement
     * explicitly says "candle close" — not intraday price action.
     */
    public boolean isDailySupertrendBreached(String underlying) {
        logger.info("[JB610-DAILY-EXIT][{}] isDailySupertrendBreached called", underlying);
        try {
            int instrumentId = IndexInstruments.fromKey(underlying).getLabel();
            String redisKey = underlying + ":" + instrumentId;

            List<CandleData> fetchedDailyCandles = candleRedisService.getCandles(redisKey, "1D", 20);
            if (fetchedDailyCandles == null || fetchedDailyCandles.size() < FAST_ST_ATR_PERIOD + 2) {
                logger.warn("[JB610-DAILY-EXIT][{}] Not enough daily candles | Count={} | Required={}",
                        underlying, fetchedDailyCandles == null ? 0 : fetchedDailyCandles.size(), FAST_ST_ATR_PERIOD + 2);
                return false;
            }

            List<CandleData> dailyCandles = new ArrayList<>(fetchedDailyCandles);
            dailyCandles.sort(Comparator.comparingLong(CandleData::getEpochTime));
            logger.info("[JB610-DAILY-EXIT][{}] Daily candles fetched | TotalCount={} | FirstEpoch={} | LastEpoch={}",
                    underlying, dailyCandles.size(),
                    dailyCandles.get(0).getEpochTime(),
                    dailyCandles.get(dailyCandles.size() - 1).getEpochTime());

            // Exclude today's incomplete candle — only evaluate fully closed daily candles
            long todayStartEpoch = LocalDate.now(ZONE_ID)
                    .atStartOfDay(ZONE_ID)
                    .toInstant()
                    .getEpochSecond();
            CandleData lastCandle = dailyCandles.get(dailyCandles.size() - 1);
            if (lastCandle.getEpochTime() >= todayStartEpoch) {
                dailyCandles.remove(dailyCandles.size() - 1);
                logger.info("[JB610-DAILY-EXIT][{}] Excluded today's incomplete candle | TodayEpoch={} | RemainingCount={}",
                        underlying, lastCandle.getEpochTime(), dailyCandles.size());
            }

            if (dailyCandles.size() < FAST_ST_ATR_PERIOD + 2) {
                logger.warn("[JB610-DAILY-EXIT][{}] Not enough completed daily candles after filtering | Count={} | Required={}",
                        underlying, dailyCandles.size(), FAST_ST_ATR_PERIOD + 2);
                return false;
            }

            double[][] dailyST = calculateSupertrendFull(dailyCandles, FAST_ST_ATR_PERIOD, FAST_ST_MULTIPLIER);
            if (dailyST == null) {
                logger.warn("[JB610-DAILY-EXIT][{}] Daily Supertrend computation returned null | CandleCount={}", underlying, dailyCandles.size());
                return false;
            }

            int lastIdx          = dailyCandles.size() - 1;
            double prevSTValue   = dailyST[lastIdx - 1][0];
            int    prevDir       = (int) dailyST[lastIdx - 1][1];
            double currSTValue   = dailyST[lastIdx][0];
            int    currDir       = (int) dailyST[lastIdx][1];
            double lastClose     = dailyCandles.get(lastIdx).getClose();
            long   lastCandleEpoch = dailyCandles.get(lastIdx).getEpochTime();

            boolean breached = prevDir != currDir;

            logger.info("[JB610-DAILY-EXIT][{}] DailyST(6,1) breach check | " +
                            "Breached={} | PrevDir={} | CurrDir={} | PrevST={} | CurrST={} | LastClose={} | LastCandleEpoch={}",
                    underlying, breached, prevDir, currDir,
                    String.format("%.2f", prevSTValue),
                    String.format("%.2f", currSTValue),
                    String.format("%.2f", lastClose),
                    lastCandleEpoch);

            return breached;
        } catch (Exception e) {
            logger.error("[JB610-DAILY-EXIT][{}] CRITICAL: Error checking daily supertrend", underlying, e);
            return false;
        }
    }
}
