package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.RedisLegState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Component
public class RedisLegService {

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashOps;

    // TTL for leg state: default 3 days
    private final long legStateTtlSeconds;

    public RedisLegService(StringRedisTemplate redis,
                           @Value("${trading.redis.legStateTtlSeconds:259200}") long legStateTtlSeconds) {
        this.redis = redis;
        this.hashOps = redis.opsForHash();
        this.legStateTtlSeconds = legStateTtlSeconds;
    }

    public String legKey(Long strategyId, Long legId) {
        return "strategy:" + strategyId + ":leg:" + legId + ":state";
    }

    public void initLegState(Long strategyId, Long legId, String underlying,
                             double executedPrice, double targetPct, double stopLossPct,
                             double trailingDistance) {

        String key = legKey(strategyId, legId);
        Map<String, String> data = new HashMap<>();
        data.put("strategyId", String.valueOf(strategyId));
        data.put("legId", String.valueOf(legId));
        data.put("executedPrice", String.valueOf(executedPrice));
        data.put("entryTimestamp", String.valueOf(Instant.now().toEpochMilli()));
        data.put("targetPct", String.valueOf(targetPct));
        data.put("stopLossPct", String.valueOf(stopLossPct));
        data.put("trailingDistance", String.valueOf(trailingDistance));
        data.put("tslActive", "0");
        data.put("tslPoints", "0");
        data.put("tslAnchor", "0");
        data.put("lastLtp", "0");
        data.put("status", "LIVE");
        data.put("updatedAt", String.valueOf(Instant.now().toEpochMilli()));

        hashOps.putAll(key, data);
        redis.expire(key, legStateTtlSeconds, TimeUnit.SECONDS);

        // register active leg by underlying
        if (underlying != null && !underlying.isEmpty()) {
            redis.opsForSet().add("activeLegs:underlying:" + underlying, key);
        }
    }

    public RedisLegState readLegState(Long strategyId, Long legId) {
        String key = legKey(strategyId, legId);
        Map<String, String> map = hashOps.entries(key);
        if (map == null || map.isEmpty()) return null;
        RedisLegState s = mapToState(map);
        return s;
    }

    public void writeLegState(RedisLegState state, String underlying) {
        if (state == null) return;
        String key = legKey(state.getStrategyId(), state.getLegId());
        Map<String, String> data = stateToMap(state);
        hashOps.putAll(key, data);
        redis.expire(key, legStateTtlSeconds, TimeUnit.SECONDS);
        if (underlying != null) {
            redis.opsForSet().add("activeLegs:underlying:" + underlying, key);
        }
    }

    public void deleteLegState(Long strategyId, Long legId, String underlying) {
        String key = legKey(strategyId, legId);
        redis.delete(key);
        if (underlying != null) {
            redis.opsForSet().remove("activeLegs:underlying:" + underlying, key);
        }
    }

    private RedisLegState mapToState(Map<String, String> map) {
        RedisLegState s = new RedisLegState();
        try {
            s.setStrategyId(Long.valueOf(map.getOrDefault("strategyId", "0")));
            s.setLegId(Long.valueOf(map.getOrDefault("legId", "0")));
            s.setExecutedPrice(parseDouble(map.get("executedPrice")));
            s.setEntryTimestamp(parseLong(map.get("entryTimestamp")));
            s.setTargetPct(parseDouble(map.get("targetPct")));
            s.setStopLossPct(parseDouble(map.get("stopLossPct")));
            s.setTslActive("1".equals(map.getOrDefault("tslActive","0")));
            s.setTslPoints(parseDouble(map.get("tslPoints")));
            s.setTslAnchor(parseDouble(map.get("tslAnchor")));
            s.setTrailingDistance(parseDouble(map.get("trailingDistance")));
            s.setLastLtp(parseDouble(map.get("lastLtp")));
            s.setStatus(map.getOrDefault("status", "LIVE"));
            s.setUpdatedAt(parseLong(map.get("updatedAt")));
        } catch (Exception e) {
            // if anything odd, return null (caller will fallback)
            return null;
        }
        return s;
    }

    private Map<String, String> stateToMap(RedisLegState s) {
        Map<String, String> m = new HashMap<>();
        m.put("strategyId", String.valueOf(s.getStrategyId()));
        m.put("legId", String.valueOf(s.getLegId()));
        m.put("executedPrice", String.valueOf(s.getExecutedPrice()));
        m.put("entryTimestamp", String.valueOf(s.getEntryTimestamp()));
        m.put("targetPct", String.valueOf(s.getTargetPct()));
        m.put("stopLossPct", String.valueOf(s.getStopLossPct()));
        m.put("tslActive", s.getTslActive() ? "1" : "0");
        m.put("tslPoints", String.valueOf(s.getTslPoints()));
        m.put("tslAnchor", String.valueOf(s.getTslAnchor()));
        m.put("trailingDistance", String.valueOf(s.getTrailingDistance()));
        m.put("lastLtp", String.valueOf(s.getLastLtp()));
        m.put("status", s.getStatus());
        m.put("updatedAt", String.valueOf(s.getUpdatedAt()));
        return m;
    }

    private Double parseDouble(String v) {
        try { return v == null ? 0.0 : Double.valueOf(v); } catch (Exception e) { return 0.0; }
    }

    private Long parseLong(String v) {
        try { return v == null ? 0L : Long.valueOf(v); } catch (Exception e) { return 0L; }
    }
    public void initEquityLegState(Long strategyId, Long legId, String symbol,
                                   double executedPrice, double dayLow,
                                   double tslActivationPct, double tslDistancePct) {
        String key = legKey(strategyId, legId);
        Map<String, String> data = new HashMap<>();
        data.put("strategyId", String.valueOf(strategyId));
        data.put("legId", String.valueOf(legId));
        data.put("symbol", symbol != null ? symbol : "");
        data.put("executedPrice", String.valueOf(executedPrice));
        data.put("entryTimestamp", String.valueOf(Instant.now().toEpochMilli()));
        data.put("dayLow", String.valueOf(dayLow));
        data.put("peakPrice", String.valueOf(executedPrice)); // Start at entry price
        data.put("trailingActivationPct", String.valueOf(tslActivationPct));
        data.put("trailingDistancePct", String.valueOf(tslDistancePct));
        data.put("tslActive", "0");
        data.put("lastLtp", "0");
        data.put("status", "LIVE");
        data.put("updatedAt", String.valueOf(Instant.now().toEpochMilli()));
        // Also include legacy fields for compatibility
        data.put("targetPct", "0");
        data.put("stopLossPct", "0");
        data.put("trailingDistance", "0");
        data.put("tslPoints", "0");
        data.put("tslAnchor", "0");

        hashOps.putAll(key, data);
        redis.expire(key, legStateTtlSeconds, TimeUnit.SECONDS);
    }
}