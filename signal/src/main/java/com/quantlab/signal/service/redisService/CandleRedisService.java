package com.quantlab.signal.service.redisService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.signal.dto.redisDto.CandleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class CandleRedisService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Push a single candle to Redis using pipelined operations.
     * Uses ZSet for ordering and Hash for data storage.
     */
    public void pushCandle(String symbol, String timeframe, CandleData candle) {
        try {
            String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
            String hashKey = zsetKey + ":data";
            String epochKey = String.valueOf(candle.getEpochTime());
            String json = mapper.writeValueAsString(candle);

            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForZSet().add(zsetKey, epochKey, candle.getEpochTime());
                    operations.opsForHash().put(hashKey, epochKey, json);
                    return null;
                }
            });

        } catch (Exception e) {
            log.error("Error pushing candle for {}:{} - {}", symbol, timeframe, e.getMessage());
        }
    }

    /**
     * Push multiple candles in a single batch operation.
     * More efficient for bulk writes.
     */
    public void pushCandlesBatch(String symbol, String timeframe, List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        try {
            String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
            String hashKey = zsetKey + ":data";

            Map<String, String> candleJsonMap = new HashMap<>();
            for (CandleData candle : candles) {
                String epochKey = String.valueOf(candle.getEpochTime());
                candleJsonMap.put(epochKey, mapper.writeValueAsString(candle));
            }

            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (CandleData candle : candles) {
                        String epochKey = String.valueOf(candle.getEpochTime());
                        operations.opsForZSet().add(zsetKey, epochKey, candle.getEpochTime());
                        operations.opsForHash().put(hashKey, epochKey, candleJsonMap.get(epochKey));
                    }
                    return null;
                }
            });

            log.debug("Batch pushed {} candles for {}:{}", candles.size(), symbol, timeframe);

        } catch (Exception e) {
            log.error("Error batch pushing candles for {}:{} - {}", symbol, timeframe, e.getMessage());
        }
    }

    /**
     * Get the last N candles for a symbol and timeframe.
     */
    public List<CandleData> getCandles(String symbol, String timeframe, int lastN) {
        String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
        String hashKey = zsetKey + ":data";

        // Primary path: list schema (current market-feed writer behavior).
        try {
            long listSize = Optional.ofNullable(stringRedisTemplate.opsForList().size(zsetKey)).orElse(0L);
            if (listSize == 0) {
                return Collections.emptyList();
            }
            long start = Math.max(0, listSize - lastN);
            long end = listSize - 1;
            List<String> jsonList = stringRedisTemplate.opsForList().range(zsetKey, start, end);
            if (jsonList == null || jsonList.isEmpty()) {
                return Collections.emptyList();
            }
            List<CandleData> candles = new ArrayList<>();
            for (String json : jsonList) {
                candles.add(mapper.readValue(json, CandleData.class));
            }
            candles.sort(Comparator.comparingLong(CandleData::getEpochTime));
            return candles;
        } catch (Exception listEx) {
            String msg = listEx.getMessage();
            if (msg == null || !msg.contains("WRONGTYPE")) {
                log.error("Error fetching candles(list) for {}:{} - {}", symbol, timeframe, msg);
                return Collections.emptyList();
            }
            log.debug("List read WRONGTYPE for {}:{}, falling back to hash/zset", symbol, timeframe);
        }

        // Fallback for legacy schema: zset + hash or hash-only.
        try {
            Set<String> epochs = stringRedisTemplate.opsForZSet().reverseRange(zsetKey, 0, lastN - 1);
            if (epochs != null && !epochs.isEmpty()) {
                return parseFromHashByEpochs(hashKey, epochs);
            }

            Map<Object, Object> all = stringRedisTemplate.opsForHash().entries(hashKey);
            if (all == null || all.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> sortedEpochsDesc = all.keySet().stream()
                    .map(Object::toString)
                    .sorted((a, b) -> Long.compare(parseEpochSafe(b), parseEpochSafe(a)))
                    .limit(lastN)
                    .toList();
            return parseFromHashByEpochs(hashKey, new LinkedHashSet<>(sortedEpochsDesc));
        } catch (Exception fallbackEx) {
            log.error("Error fetching candles(fallback) for {}:{} - {}", symbol, timeframe, fallbackEx.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CandleData> getCandlesByRange(String symbol, String timeframe, long fromEpoch, long toEpoch) {
        try {
            String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
            String hashKey = zsetKey + ":data";

            Set<String> epochs = stringRedisTemplate.opsForZSet()
                    .rangeByScore(zsetKey, fromEpoch, toEpoch);

            if (epochs == null || epochs.isEmpty()) {
                return Collections.emptyList();
            }

            List<CandleData> candles = new ArrayList<>();
            List<Object> jsonValues = stringRedisTemplate.opsForHash()
                    .multiGet(hashKey, new ArrayList<>(epochs));

            if (jsonValues != null) {
                for (Object json : jsonValues) {
                    if (json != null) {
                        candles.add(mapper.readValue((String) json, CandleData.class));
                    }
                }
            }

            candles.sort(Comparator.comparingLong(CandleData::getEpochTime));
            return candles;

        } catch (Exception e) {
            log.error("Error fetching candles by range for {}:{} - {}", symbol, timeframe, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<CandleData> getLatestCandle(String symbol, String timeframe) {
        List<CandleData> candles = getCandles(symbol, timeframe, 1);
        return candles.isEmpty() ? Optional.empty() : Optional.of(candles.get(0));
    }

    public long deleteOldCandles(String symbol, String timeframe, long beforeEpoch) {
        try {
            String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
            String hashKey = zsetKey + ":data";

            Set<String> epochsToDelete = stringRedisTemplate.opsForZSet()
                    .rangeByScore(zsetKey, 0, beforeEpoch);

            if (epochsToDelete == null || epochsToDelete.isEmpty()) {
                return 0;
            }

            stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForZSet().removeRangeByScore(zsetKey, 0, beforeEpoch);
                    operations.opsForHash().delete(hashKey, epochsToDelete.toArray());
                    return null;
                }
            });

            log.info("Deleted {} old candles for {}:{}", epochsToDelete.size(), symbol, timeframe);
            return epochsToDelete.size();

        } catch (Exception e) {
            log.error("Error deleting old candles for {}:{} - {}", symbol, timeframe, e.getMessage());
            return 0;
        }
    }

    public long getCandleCount(String symbol, String timeframe) {
        try {
            String zsetKey = String.format("candle:%s:%s", symbol, timeframe);
            Long count = stringRedisTemplate.opsForZSet().zCard(zsetKey);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error getting candle count for {}:{} - {}", symbol, timeframe, e.getMessage());
            return 0;
        }
    }

    private List<CandleData> parseFromHashByEpochs(String hashKey, Set<String> epochs) throws Exception {
        List<Object> jsonValues = stringRedisTemplate.opsForHash().multiGet(hashKey, new ArrayList<>(epochs));
        List<CandleData> candles = new ArrayList<>();
        if (jsonValues != null) {
            for (Object json : jsonValues) {
                if (json != null) {
                    candles.add(mapper.readValue((String) json, CandleData.class));
                }
            }
        }
        candles.sort(Comparator.comparingLong(CandleData::getEpochTime));
        return candles;
    }

    private long parseEpochSafe(String epoch) {
        try {
            return Long.parseLong(epoch);
        } catch (NumberFormatException ex) {
            return Long.MIN_VALUE;
        }
    }
}
